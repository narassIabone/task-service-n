package ru.cinimex.taskservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.cinimex.taskservice.dto.TaskRequestDto;
import ru.cinimex.taskservice.dto.TaskResponseDto;
import ru.cinimex.taskservice.dto.TaskSearchFilter;

import java.util.List;
import java.util.UUID;

@Tag(name = "Task API", description = "Управление задачами пользователя")
@RequestMapping("/tasks")
public interface TaskControllerApi {

    @Operation(summary = "Создать новую задачу", description = "Доступно пользователям с ролью USER")
    @PostMapping
    ResponseEntity<UUID> createTask(@Valid @RequestBody TaskRequestDto request);

    @Operation(summary = "Получить список задач", description = "Поиск задач текущего пользователя с фильтрацией")
    @GetMapping
    ResponseEntity<List<TaskResponseDto>> getTasks(TaskSearchFilter filter);

    @Operation(summary = "Получить задачу по ID", description = "Возвращает данные задачи, если она принадлежит текущему пользователю")
    @GetMapping("/{id}")
    ResponseEntity<TaskResponseDto> getTaskById(@PathVariable("id") UUID id);

    @Operation(summary = "Обновить задачу", description = "Обновление полей задачи в статусе CREATED")
    @PutMapping("/{id}")
    ResponseEntity<Void> updateTask(
            @PathVariable("id") UUID id,
            @Valid @RequestBody TaskRequestDto request
    );

    @Operation(summary = "Удалить задачу", description = "Удаление задачи в статусе CREATED")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteTask(@PathVariable("id") UUID id);
}