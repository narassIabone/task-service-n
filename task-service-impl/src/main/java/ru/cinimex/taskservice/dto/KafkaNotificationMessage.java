package ru.cinimex.taskservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KafkaNotificationMessage {
    private String email;
    private String title;
    private String description;
}