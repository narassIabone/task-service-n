package ru.cinimex.taskservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import ru.cinimex.taskservice.dto.KafkaNotificationMessage;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.topic-name:notification.message.in}")
    private String topicName;

    @Bean
    public NewTopic taskNotificationTopic() {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, KafkaNotificationMessage> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, KafkaNotificationMessage> kafkaTemplate(ProducerFactory<String, KafkaNotificationMessage> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}