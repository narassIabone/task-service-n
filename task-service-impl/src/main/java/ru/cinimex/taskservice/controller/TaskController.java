package ru.cinimex.taskservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.cinimex.taskservice.dto.TaskRequestDto;
import ru.cinimex.taskservice.dto.TaskResponseDto;
import ru.cinimex.taskservice.dto.TaskSearchFilter;
import ru.cinimex.taskservice.service.TaskService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TaskController implements TaskControllerApi {

    private final TaskService taskService;

    @Override
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<UUID> createTask(TaskRequestDto request) {
        return ResponseEntity.ok(taskService.createTask(request));
    }

    @Override
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<List<TaskResponseDto>> getTasks(TaskSearchFilter filter) {
        return ResponseEntity.ok(taskService.getTasks(filter));
    }

    @Override
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<TaskResponseDto> getTaskById(UUID id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @Override
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<Void> updateTask(UUID id, TaskRequestDto request) {
        taskService.updateTask(id, request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<Void> deleteTask(UUID id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }
}