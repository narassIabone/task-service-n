package ru.cinimex.taskservice.mapper;

import org.mapstruct.*;
import org.mapstruct.Mapper;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.cinimex.taskservice.dto.TaskRequestDto;
import ru.cinimex.taskservice.dto.TaskResponseDto;
import ru.cinimex.taskservice.domain.TaskEntity;
import ru.cinimex.taskservice.domain.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true))
public abstract class TaskMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract TaskEntity toEntity(TaskRequestDto dto);

    public abstract TaskResponseDto toResponseDto(TaskEntity entity);

    public abstract void updateEntityFromDto(TaskRequestDto dto, @MappingTarget TaskEntity entity);

    @AfterMapping
    protected void fillSystemFields(TaskRequestDto dto, @MappingTarget TaskEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getStatus() == null) {
            entity.setId(UUID.randomUUID());
            entity.setStatus(TaskStatus.CREATED);
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        entity.setAssignee(auth.getName());
    }
}
