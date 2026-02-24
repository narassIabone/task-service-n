package ru.cinimex.taskservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.cinimex.taskservice.domain.TaskEntity;
import ru.cinimex.taskservice.domain.TaskStatus;
import ru.cinimex.taskservice.dto.TaskRequestDto;
import ru.cinimex.taskservice.dto.TaskResponseDto;
import ru.cinimex.taskservice.dto.TaskSearchFilter;
import ru.cinimex.taskservice.exception.TaskValidationException;
import ru.cinimex.taskservice.mapper.TaskMapper;
import ru.cinimex.taskservice.repository.TaskRepository;
import ru.cinimex.taskservice.repository.TaskSpecification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;

    @Transactional
    public UUID createTask(TaskRequestDto request) {
        if (request.getNotificateAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Ошибка: дата уведомления не может быть раньше текущей даты");
        }
        TaskEntity entity = taskMapper.toEntity(request);
        return taskRepository.save(entity).getId();
    }

    public TaskResponseDto getTaskById(UUID id) {
        String currentLogin = SecurityContextHolder.getContext().getAuthentication().getName();

        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskValidationException("Задача с id " + id + " не найдена"));

        if (!entity.getAssignee().equals(currentLogin)) {
            throw new TaskValidationException("Отказано в доступе: вы не являетесь владельцем этой задачи");
        }

        return taskMapper.toResponseDto(entity);
    }

    public List<TaskResponseDto> getTasks(TaskSearchFilter filter) {
        String currentLogin = SecurityContextHolder.getContext().getAuthentication().getName();

        Specification<TaskEntity> spec = TaskSpecification.build(filter, currentLogin);
        List<TaskEntity> entities = taskRepository.findAll(spec);

        return entities.stream()
                .map(taskMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateTask(UUID id, TaskRequestDto request) {
        String currentLogin = SecurityContextHolder.getContext().getAuthentication().getName();

        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskValidationException("Задача с id " + id + " не найдена"));

        if (entity.getStatus() != TaskStatus.CREATED) {
            throw new TaskValidationException("Редактирование запрещено: статус задачи не равен CREATED");
        }

        if (!entity.getAssignee().equals(currentLogin)) {
            throw new TaskValidationException("Отказано в доступе: вы не являетесь владельцем этой задачи");
        }

        taskMapper.updateEntityFromDto(request, entity);

        taskRepository.save(entity);
    }

    @Transactional
    public void deleteTask(UUID id) {
        String currentLogin = SecurityContextHolder.getContext().getAuthentication().getName();

        TaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new TaskValidationException("Задача с id " + id + " не найдена"));

        if (entity.getStatus() != TaskStatus.CREATED) {
            throw new TaskValidationException("Удаление запрещено: удалить задачу можно только со статусом CREATED");
        }

        if (!entity.getAssignee().equals(currentLogin)) {
            throw new TaskValidationException("Отказано в доступе: вы не можете удалить чужую задачу");
        }

        taskRepository.delete(entity);
    }
}
