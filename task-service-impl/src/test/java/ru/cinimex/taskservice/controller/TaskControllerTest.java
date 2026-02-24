package ru.cinimex.taskservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.cinimex.taskservice.dto.TaskRequestDto;
import ru.cinimex.taskservice.dto.TaskResponseDto;
import ru.cinimex.taskservice.exception.TaskValidationException;
import ru.cinimex.taskservice.service.JwtService;
import ru.cinimex.taskservice.service.TaskService;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureJsonTesters
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TaskService taskService;

    @Autowired
    private JacksonTester<TaskRequestDto> taskRequestJson;

    @Autowired
    private JacksonTester<List<TaskResponseDto>> taskListJson;

    // Вспомогательный метод для генерации токена
    private String getAuthHeader(String username, String role) {
        String token = jwtService.generateTestToken(username, role, OffsetDateTime.now().plusHours(1));
        return "Bearer " + token;
    }

    private final String USER_LOGIN = "test_user";

    // --- 1. POST /tasks ---
    @Nested
    @DisplayName("POST /tasks")
    class CreateTaskTests {
        @Test
        @DisplayName("200 OK - Успешное создание задачи")
        void createSuccess() throws Exception {
            UUID taskId = UUID.randomUUID();
            TaskRequestDto request = new TaskRequestDto("Task 1", "Description", LocalDateTime.now().plusDays(1));

            when(taskService.createTask(any(TaskRequestDto.class))).thenReturn(taskId);

            mockMvc.perform(post("/tasks")
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(request).getJson()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("\"" + taskId + "\""));
        }

        @Test
        @DisplayName("401 Forbidden - Пользователь не авторизован")
        void createUnauthorized() throws Exception {
            TaskRequestDto request = new TaskRequestDto("Task 1", "Description", LocalDateTime.now().plusDays(1));
            mockMvc.perform(post("/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(request).getJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 Forbidden - У пользователя нет роли USER")
        void createForbidden() throws Exception {
            TaskRequestDto request = new TaskRequestDto("Title", "Desc", LocalDateTime.now().plusDays(1));

            mockMvc.perform(post("/tasks")
                            .header("Authorization", getAuthHeader(USER_LOGIN, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(request).getJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("500 Internal Server Error - Ошибка соединения с БД")
        void createServerError() throws Exception {
            TaskRequestDto request = new TaskRequestDto("Task 1", "Description", LocalDateTime.now().plusDays(1));
            String errorMessage = "Ошибка соединения с БД";

            when(taskService.createTask(any(TaskRequestDto.class)))
                    .thenThrow(new RuntimeException(errorMessage));

            mockMvc.perform(post("/tasks")
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(request).getJson()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMessage)));
        }
    }

    // --- 2. GET /tasks ---
    @Nested
    @DisplayName("GET /tasks")
    class GetAllTasksTests {

        private final String url = "/tasks";

        @Test
        @DisplayName("200 OK - Получение списка задач без фильтров")
        void getTasksSuccess() throws Exception {
            // Подготовка данных
            TaskResponseDto task = TaskResponseDto.builder()
                    .id(UUID.randomUUID())
                    .title("Test Task")
                    .description("Desc")
                    .status("CREATED")
                    .assignee(USER_LOGIN)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(taskService.getTasks(any())).thenReturn(List.of(task));

            mockMvc.perform(get(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Test Task"))
                    .andExpect(jsonPath("$[0].assignee").value(USER_LOGIN));
        }

        @Test
        @DisplayName("200 OK - Получение списка задач с фильтрами")
        void getTasksWithFiltersSuccess() throws Exception {
            // Имитируем параметры фильтрации
            String title = "Work";
            String status = "CREATED";
            String start = "2024-08-14T10:00:00";
            String end = "2024-08-14T20:00:00";

            when(taskService.getTasks(any())).thenReturn(List.of());

            mockMvc.perform(get(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER"))
                            .param("title", title)
                            .param("status", status)
                            .param("notificate_at_start", start)
                            .param("notificate_at_end", end))
                    .andExpect(status().isOk());

            // Проверяем, что в сервис передались параметры (через ArgumentCaptor или проверку any())
            verify(taskService).getTasks(any());
        }

        @Test
        @DisplayName("200 OK - Возврат пустого списка, если задач нет")
        void getTasksEmpty() throws Exception {
            when(taskService.getTasks(any())).thenReturn(List.of());

            mockMvc.perform(get(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("401 Unauthorized - Токен не передан")
        void getTasksUnauthorized() throws Exception {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 Forbidden - Доступ запрещен для роли ADMIN (нужен USER)")
        void getTasksForbidden() throws Exception {
            mockMvc.perform(get(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("500 Internal Server Error - Ошибка БД")
        void getTasksServerError() throws Exception {
            String errorMsg = "Database failure during search";
            when(taskService.getTasks(any())).thenThrow(new RuntimeException(errorMsg));

            mockMvc.perform(get(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMsg)));
        }
    }

    // --- 3. GET /tasks/{id} ---
    @Nested
    @DisplayName("GET /tasks/{id}")
    class GetTaskByIdTests {
        @Test
        @DisplayName("200 OK - Успех")
        void getByIdSuccess() throws Exception {
            UUID id = UUID.fromString("28b60e51-656b-4432-a493-cedb5cf5f72d");

            TaskResponseDto task = TaskResponseDto.builder()
                    .id(id)
                    .title("Title")
                    .assignee(USER_LOGIN)
                    .status("CREATED")
                    .build();

            when(taskService.getTaskById(id)).thenReturn(task);

            mockMvc.perform(get("/tasks/" + id)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));
        }

        @Test
        @DisplayName("400 Bad Request - Задачи не существует / вы не являетесь владельцем задачи")
        void getByIdBadRequest() throws Exception {
            UUID id = UUID.randomUUID();
            String errorMsg = "Ошибка - Задачи не существует / вы не являетесь владельцем задачи";

            when(taskService.getTaskById(id)).thenThrow(new TaskValidationException(errorMsg));

            mockMvc.perform(get("/tasks/" + id)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMsg)));
        }

        @Test
        @DisplayName("401 Forbidden - Пользователь не авторизован")
        void getIsUnauthorized() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(get("/tasks/" + id))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 Forbidden - У пользователя нет роли USER")
        void getIsForbidden() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(get("/tasks/" + id)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("500 Internal Server Error - Непредвиденная ошибка при получении задачи по ID")
        void getByIdServerError() throws Exception {
            UUID taskId = UUID.randomUUID();
            String fatalError = "Внутренняя ошибка сервера";

            when(taskService.getTaskById(taskId))
                    .thenThrow(new RuntimeException(fatalError));

            mockMvc.perform(get("/tasks/" + taskId)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(fatalError)));
        }

    }

    // --- 4. PUT /tasks/{id} ---
    @Nested
    @DisplayName("PUT /tasks/{id}")
    class UpdateTaskTests {

        private final UUID taskId = UUID.randomUUID();
        private final String url = "/tasks/" + taskId;

        private TaskRequestDto createRequest() {
            return new TaskRequestDto("Updated Title", "Updated Desc", LocalDateTime.now().plusDays(1));
        }
        @Test
        @DisplayName("200 OK - Успешное обновление")
        void updateSuccess() throws Exception {
            TaskRequestDto request = createRequest();

            mockMvc.perform(put(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(request).getJson()))
                    .andExpect(status().isOk());

            verify(taskService, times(1)).updateTask(eq(taskId), any());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Задача с id не найдена",
                "Редактирование запрещено: задача уже находится в работе",
                "Отказано в доступе: вы не являетесь владельцем этой задачи"
        })
        @DisplayName("400 Bad Request - Различные ошибки валидации при обновлении")
        void updateValidationErrors(String errorMsg) throws Exception {
            TaskRequestDto request = createRequest();

            doThrow(new TaskValidationException(errorMsg))
                    .when(taskService).updateTask(eq(taskId), any(TaskRequestDto.class));

            mockMvc.perform(put(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(request).getJson()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMsg)));
        }

        @Test
        @DisplayName("401 Unauthorized - Токен не передан")
        void updateUnauthorized() throws Exception {
            mockMvc.perform(put(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(createRequest()).getJson()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 Forbidden - У пользователя нет роли USER")
        void updateForbiddenRole() throws Exception {
            mockMvc.perform(put(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(createRequest()).getJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("500 Internal Server Error - Системная ошибка")
        void updateServerError() throws Exception {
            String errorMsg = "Database error";
            doThrow(new RuntimeException(errorMsg))
                    .when(taskService).updateTask(any(), any());

            mockMvc.perform(put(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(taskRequestJson.write(createRequest()).getJson()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMsg)));
        }
    }

    // --- 5. DELETE /tasks/{id} ---
    @Nested
    @DisplayName("DELETE /tasks/{id}")
    class DeleteTaskTests {

        private final UUID taskId = UUID.randomUUID();
        private final String url = "/tasks/" + taskId;

        @Test
        @DisplayName("200 OK - Успешное удаление задачи")
        void deleteSuccess() throws Exception {
            // Обучать мок doNothing() не обязательно, это поведение по умолчанию для void
            mockMvc.perform(delete(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isOk());

            // Проверяем, что метод сервиса был вызван
            verify(taskService, times(1)).deleteTask(taskId);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Задача с id не найдена",
                "Удаление запрещено: задача уже находится в работе",
                "Отказано в доступе: вы не являетесь владельцем этой задачи"
        })
        @DisplayName("400 Bad Request - Ошибки валидации при удалении")
        void deleteValidationErrors(String errorMsg) throws Exception {
            // Настраиваем выброс исключения при вызове deleteTask
            doThrow(new TaskValidationException(errorMsg))
                    .when(taskService).deleteTask(taskId);

            mockMvc.perform(delete(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMsg)));
        }

        @Test
        @DisplayName("401 Unauthorized - Токен не передан")
        void deleteUnauthorized() throws Exception {
            mockMvc.perform(delete(url))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 Forbidden - Неверная роль")
        void deleteForbiddenRole() throws Exception {
            mockMvc.perform(delete(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("500 Internal Server Error - Системная ошибка при удалении")
        void deleteServerError() throws Exception {
            String errorMsg = "Внутрення ошибка при удалении из БД";
            doThrow(new RuntimeException(errorMsg))
                    .when(taskService).deleteTask(any());

            mockMvc.perform(delete(url)
                            .header("Authorization", getAuthHeader(USER_LOGIN, "USER")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(errorMsg)));
        }
    }
}