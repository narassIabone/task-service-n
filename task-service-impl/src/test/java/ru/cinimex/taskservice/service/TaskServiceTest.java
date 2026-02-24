package ru.cinimex.taskservice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.cinimex.taskservice.domain.TaskEntity;
import ru.cinimex.taskservice.domain.TaskStatus;
import ru.cinimex.taskservice.dto.TaskRequestDto;
import ru.cinimex.taskservice.dto.TaskResponseDto;
import ru.cinimex.taskservice.dto.TaskSearchFilter;
import ru.cinimex.taskservice.exception.TaskValidationException;
import ru.cinimex.taskservice.mapper.TaskMapper;
import ru.cinimex.taskservice.mapper.TaskMapperImpl; // Убедитесь, что проект скомпилирован и класс сгенерирован
import ru.cinimex.taskservice.repository.TaskRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    // Используем Spy, чтобы работал реальный метод fillSystemFields в маппере
    @Spy
    private TaskMapper taskMapper = new TaskMapperImpl();

    @InjectMocks
    private TaskService taskService;

    private final String CURRENT_USER = "test_user";

    @BeforeEach
    void setUp() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(CURRENT_USER);

        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);

        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        // Очищаем контекст, чтобы не влиять на другие тесты
        SecurityContextHolder.clearContext();
    }

    // --- 1. CREATE TASK ---
    @Nested
    @DisplayName("Метод createTask")
    class CreateTaskTests {

        @Test
        @DisplayName("Успех: создание задачи со всеми системными полями")
        void createSuccess() {
            TaskRequestDto request = new TaskRequestDto("Купить молока", "В магазине", LocalDateTime.now().plusDays(1));

            // Настраиваем репозиторий: просто возвращаем то, что пришло на вход
            when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UUID resultId = taskService.createTask(request);

            assertNotNull(resultId);
            verify(taskRepository).save(argThat(entity ->
                    entity.getTitle().equals("Купить молока") &&
                            entity.getAssignee().equals(CURRENT_USER) &&
                            entity.getStatus() == TaskStatus.CREATED &&
                            entity.getId() != null
            ));
        }

        @Test
        @DisplayName("Ошибка: дата уведомления в прошлом")
        void createFailDateInPast() {
            TaskRequestDto request = new TaskRequestDto("Title", "Desc", LocalDateTime.now().minusMinutes(1));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> taskService.createTask(request));
            assertTrue(ex.getMessage().contains("Ошибка: дата уведомления не может быть раньше текущей даты"));
            verifyNoInteractions(taskRepository);
        }
    }

    // --- 2. GET TASK BY ID ---
    @Nested
    @DisplayName("Метод getTaskById")
    class GetByIdTests {

        @Test
        @DisplayName("Успех: получение своей задачи")
        void getSuccess() {
            // Подготовка
            UUID id = UUID.randomUUID();
            LocalDateTime notificationDate = LocalDateTime.now().plusDays(1);

            TaskEntity entity = TaskEntity.builder()
                    .id(id)
                    .title("Task 1")
                    .description("Some description")
                    .assignee(CURRENT_USER)
                    .status(TaskStatus.CREATED)
                    .notificateAt(notificationDate)
                    .build();

            when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

            TaskResponseDto result = taskService.getTaskById(id);

            assertAll("Проверка всех значимых полей DTO",
                    () -> assertEquals(id, result.getId(), "ID должен совпадать"),
                    () -> assertEquals("Task 1", result.getTitle()),
                    () -> assertEquals(CURRENT_USER, result.getAssignee()),
                    () -> assertEquals(TaskStatus.CREATED.name(), result.getStatus()),
                    () -> assertEquals(notificationDate, result.getNotificateAt())
            );
        }

        @Test
        @DisplayName("Ошибка 400: задача не найдена")
        void getNotFound() {
            UUID randomId = UUID.randomUUID();
            when(taskRepository.findById(randomId)).thenReturn(Optional.empty());

            TaskValidationException exception = assertThrows(TaskValidationException.class, () -> {
                taskService.getTaskById(randomId);
            });

            assertEquals("Задача с id " + randomId + " не найдена", exception.getMessage());
            verifyNoInteractions(taskMapper);
        }

        @Test
        @DisplayName("Ошибка 400: попытка доступа к чужой задаче")
        void getAccessDenied() {
            UUID taskId = UUID.randomUUID();
            String owner = "another_user";

            TaskEntity entity = TaskEntity.builder()
                    .id(taskId)
                    .assignee(owner)
                    .status(TaskStatus.CREATED)
                    .build();

            when(taskRepository.findById(taskId)).thenReturn(Optional.of(entity));

            TaskValidationException exception = assertThrows(TaskValidationException.class, () -> {
                taskService.getTaskById(taskId);
            });

            assertEquals("Отказано в доступе: вы не являетесь владельцем этой задачи", exception.getMessage());
            verify(taskMapper, never()).toResponseDto(any());
        }
    }

    // --- 3. UPDATE TASK ---
    @Nested
    @DisplayName("Метод updateTask")
    class UpdateTaskFullTests {

        private final UUID taskId = UUID.randomUUID();
        private final TaskRequestDto requestDto = new TaskRequestDto("New Title", "New Desc", LocalDateTime.now().plusDays(1));

        @Test
        @DisplayName("Успех: полная проверка обновления задачи")
        void updateSuccess() {
            // 1. Подготовка: существующая задача в статусе CREATED и принадлежит текущему пользователю
            TaskEntity existingEntity = TaskEntity.builder()
                    .id(taskId)
                    .title("Old Title")
                    .assignee(CURRENT_USER)
                    .status(TaskStatus.CREATED)
                    .build();

            when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingEntity));

            // 2. Выполнение
            taskService.updateTask(taskId, requestDto);

            // 3. Проверки
            // Проверяем, что маппер был вызван для обновления полей
            verify(taskMapper).updateEntityFromDto(eq(requestDto), eq(existingEntity));
            // Проверяем, что обновленная сущность сохранена в БД
            verify(taskRepository).save(existingEntity);
        }

        @Test
        @DisplayName("Ошибка: Задача не найдена")
        void updateNotFound() {
            when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

            TaskValidationException ex = assertThrows(TaskValidationException.class,
                    () -> taskService.updateTask(taskId, requestDto));

            assertEquals("Задача с id " + taskId + " не найдена", ex.getMessage());
            verify(taskRepository, never()).save(any(TaskEntity.class));
        }

        @Test
        @DisplayName("Ошибка: Статус задачи не CREATED")
        void updateWrongStatus() {
            // Подготовка: задача существует, но уже в работе
            TaskEntity entityInProgress = TaskEntity.builder()
                    .id(taskId)
                    .assignee(CURRENT_USER)
                    .status(TaskStatus.ERROR)
                    .build();

            when(taskRepository.findById(taskId)).thenReturn(Optional.of(entityInProgress));

            TaskValidationException ex = assertThrows(TaskValidationException.class,
                    () -> taskService.updateTask(taskId, requestDto));

            assertEquals("Редактирование запрещено: статус задачи не равен CREATED", ex.getMessage());
            // Проверяем, что до сохранения дело не дошло
            verify(taskRepository, never()).save(any(TaskEntity.class));
        }

        @Test
        @DisplayName("Ошибка: Пользователь не владелец задачи")
        void updateNotOwner() {
            // Подготовка: статус верный, но владелец другой
            TaskEntity foreignEntity = TaskEntity.builder()
                    .id(taskId)
                    .assignee("another_user")
                    .status(TaskStatus.CREATED)
                    .build();

            when(taskRepository.findById(taskId)).thenReturn(Optional.of(foreignEntity));

            TaskValidationException ex = assertThrows(TaskValidationException.class,
                    () -> taskService.updateTask(taskId, requestDto));

            assertEquals("Отказано в доступе: вы не являетесь владельцем этой задачи", ex.getMessage());
            verify(taskRepository, never()).save(any(TaskEntity.class));
            // Также проверяем, что данные в сущность не вносились
            verify(taskMapper, never()).updateEntityFromDto(any(), any());
        }
    }

    // --- 4. DELETE TASK ---
    @Nested
    @DisplayName("Метод deleteTask")
    class DeleteTaskTests {

        @Test
        @DisplayName("Успех: удаление своей задачи в статусе CREATED")
        void deleteSuccess() {
            UUID id = UUID.randomUUID();
            TaskEntity entity = TaskEntity.builder()
                    .id(id).assignee(CURRENT_USER).status(TaskStatus.CREATED).build();

            when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

            taskService.deleteTask(id);

            verify(taskRepository).delete(entity);
        }

        @Test
        @DisplayName("Ошибка: статус задачи не позволяет удаление")
        void deleteForbiddenStatus() {
            UUID id = UUID.randomUUID();
            TaskEntity entity = TaskEntity.builder()
                    .id(id).assignee(CURRENT_USER).status(TaskStatus.DONE).build();

            when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

            TaskValidationException ex = assertThrows(TaskValidationException.class, () -> taskService.deleteTask(id));
            assertTrue(ex.getMessage().contains("Удаление запрещено: удалить задачу можно только со статусом CREATED"));
        }

        @Test
        @DisplayName("Ошибка: задача не найдена")
        void deleteNotFound() {
            UUID id = UUID.randomUUID();
            when(taskRepository.findById(id)).thenReturn(Optional.empty());

            TaskValidationException ex = assertThrows(TaskValidationException.class,
                    () -> taskService.deleteTask(id));

            assertTrue(ex.getMessage().contains("Задача с id " + id + " не найдена"));
            verify(taskRepository, never()).delete(any(TaskEntity.class));
        }

        @Test
        @DisplayName("Ошибка: попытка удалить чужую задачу (Assignee не совпадает)")
        void deleteNotOwner() {
            UUID id = UUID.randomUUID();
            TaskEntity foreignTask = TaskEntity.builder()
                    .id(id)
                    .assignee("someone_else")
                    .status(TaskStatus.CREATED)
                    .build();

            when(taskRepository.findById(id)).thenReturn(Optional.of(foreignTask));

            TaskValidationException ex = assertThrows(TaskValidationException.class,
                    () -> taskService.deleteTask(id));

            assertTrue(ex.getMessage().contains("Отказано в доступе: вы не можете удалить чужую задачу"));
            verify(taskRepository, never()).delete(any(TaskEntity.class));
        }
    }

    // --- 5. GET TASKS ---
    @Nested
    @DisplayName("Метод getTasks")
    class GetTasksTests {

        @Test
        @DisplayName("Успех: получение списка задач")
        void getTasksSuccess() {
            // 1. Подготовка данных
            TaskSearchFilter filter = new TaskSearchFilter();
            // Заполним фильтр какими-нибудь данными
            filter.setTitle("Сходить в спортзал");

            TaskEntity entity = TaskEntity.builder()
                    .id(UUID.randomUUID())
                    .title("Сходить в спортзал")
                    .assignee(CURRENT_USER)
                    .build();

            // Настраиваем репозиторий: на любую спецификацию возвращаем список с одной сущностью
            when(taskRepository.findAll(any(Specification.class))).thenReturn(List.of(entity));

            // 2. Выполнение
            List<TaskResponseDto> result = taskService.getTasks(filter);

            // 3. Проверки
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("Сходить в спортзал", result.get(0).getTitle());

            // Проверяем, что маппер был вызван для каждого элемента списка
            verify(taskMapper, times(1)).toResponseDto(entity);

            // Проверяем, что в репозиторий действительно ушла какая-то спецификация
            verify(taskRepository).findAll(any(Specification.class));
        }

        @Test
        @DisplayName("Успех: возврат пустого списка, если задачи не найдены")
        void getTasksEmptyResult() {
            TaskSearchFilter filter = new TaskSearchFilter();

            when(taskRepository.findAll(any(Specification.class))).thenReturn(List.of());

            List<TaskResponseDto> result = taskService.getTasks(filter);

            assertTrue(result.isEmpty());
            verify(taskRepository).findAll(any(Specification.class));
            verify(taskMapper, never()).toResponseDto(any());
        }

        @Test
        @DisplayName("Ошибка: исключение при работе с базой данных")
        void getTasksDatabaseError() {
            when(taskRepository.findAll(any(Specification.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            assertThrows(RuntimeException.class, () -> taskService.getTasks(new TaskSearchFilter()));
        }
    }
}