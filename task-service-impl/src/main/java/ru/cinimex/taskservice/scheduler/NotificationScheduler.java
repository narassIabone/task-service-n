package ru.cinimex.taskservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.cinimex.taskservice.domain.TaskEntity;
import ru.cinimex.taskservice.domain.TaskStatus;
import ru.cinimex.taskservice.dto.KafkaNotificationMessage;
import ru.cinimex.taskservice.dto.UserResponseDto;
import ru.cinimex.taskservice.repository.TaskRepository;
import ru.cinimex.taskservice.feign.UserServiceClient;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduler {

    private final TaskRepository taskRepository;
    private final UserServiceClient userServiceClient;
    private final KafkaTemplate<String, KafkaNotificationMessage> kafkaTemplate;

    @Value("${app.scheduler.batch-size:5}")
    private int batchSize;

    @Value("${app.security.technical-token}")
    private String techToken;

    @Value("${spring.kafka.topic-name:task-notifications}")
    private String topicName;

    /**
     * Шедулер для обработки задач, требующих уведомления.
     * Использует Cron-выражение из конфигурации.
     */
    @Transactional
    @Scheduled(cron = "${app.scheduler.cron:0 * * * * *}")
    public void processPendingNotifications() {
        log.debug("Notification scheduler started at {}", LocalDateTime.now());

        // 1. Получаем и блокируем N записей (Pessimistic Write + Skip Locked)
        // Метод в репозитории должен быть помечен @Lock
        List<TaskEntity> tasks = taskRepository.findTasksToNotify(
                LocalDateTime.now(),
                PageRequest.of(0, batchSize)
        );

        if (tasks.isEmpty()) {
            return;
        }

        log.info("Found {} tasks to notify", tasks.size());
        String authHeader = "Bearer " + techToken;

        for (TaskEntity task : tasks) {
            try {
                // 2. Получаем email пользователя из User-Service
                log.debug("Fetching email for user: {}", task.getAssignee());
                UserResponseDto user = userServiceClient.getUserByLogin(task.getAssignee(), authHeader);

                // 3. Формируем сообщение для Kafka
                KafkaNotificationMessage message = new KafkaNotificationMessage(
                        user.getEmail(),
                        task.getTitle(),
                        task.getDescription()
                );

                // 4. Отправляем в Kafka (синхронно или асинхронно)
                // Используем ID задачи как ключ сообщения для обеспечения порядка (если нужно)
                kafkaTemplate.send(topicName, task.getId().toString(), message)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.debug("Sent message to Kafka for task {}", task.getId());
                            } else {
                                log.error("Failed to send message to Kafka for task {}", task.getId(), ex);
                            }
                        });

                // 5. Успешное завершение — переводим в DONE
                task.setStatus(TaskStatus.DONE);

            } catch (Exception e) {
                log.error("Failed to process notification for task {}. Error: {}", task.getId(), e.getMessage());
                // 6. Ошибка при вызове Feign или других операциях — переводим в ERROR
                task.setStatus(TaskStatus.ERROR);
            }

            task.setUpdatedAt(LocalDateTime.now());
        }

        // 7. Сохраняем изменения пачкой. Блокировки отпустятся после завершения @Transactional метода.
        taskRepository.saveAll(tasks);
        log.info("Finished processing batch of {} tasks", tasks.size());
    }
}