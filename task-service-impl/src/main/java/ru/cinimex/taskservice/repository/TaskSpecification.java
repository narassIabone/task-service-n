package ru.cinimex.taskservice.repository;

import org.springframework.data.jpa.domain.Specification;
import ru.cinimex.taskservice.domain.TaskEntity;
import ru.cinimex.taskservice.dto.TaskSearchFilter;

import java.time.LocalDateTime;

public class TaskSpecification {

    public static Specification<TaskEntity> build(TaskSearchFilter filter, String assignee) {
        return Specification.where(hasAssignee(assignee)) // Обязательное условие: только свои задачи
                .and(hasTitle(filter.getTitle()))
                .and(hasStatus(filter.getStatus()))
                .and(notificateAtBetween(filter.getNotificateAtStart(), filter.getNotificateAtEnd()));
    }

    private static Specification<TaskEntity> hasAssignee(String assignee) {
        return (root, query, cb) -> cb.equal(root.get("assignee"), assignee);
    }

    private static Specification<TaskEntity> hasTitle(String title) {
        return (root, query, cb) -> (title == null || title.isEmpty())
                ? null
                : cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
    }

    private static Specification<TaskEntity> hasStatus(String status) {
        return (root, query, cb) -> (status == null || status.isEmpty())
                ? null
                : cb.equal(root.get("status"), status);
    }

    private static Specification<TaskEntity> notificateAtBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start != null && end != null) {
                return cb.between(root.get("notificateAt"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("notificateAt"), start);
            } else if (end != null) {
                return cb.lessThanOrEqualTo(root.get("notificateAt"), end);
            }
            return null;
        };
    }
}
