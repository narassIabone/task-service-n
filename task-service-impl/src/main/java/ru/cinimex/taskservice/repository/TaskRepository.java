package ru.cinimex.taskservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import ru.cinimex.taskservice.domain.TaskEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID>, JpaSpecificationExecutor<TaskEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}) // -2 для SKIP LOCKED в Postgres
    @Query("SELECT t FROM TaskEntity t WHERE t.status = 'CREATED' AND t.notificateAt <= :now")
    List<TaskEntity> findTasksToNotify(LocalDateTime now, Pageable pageable);
}
