package iuh.fit.reportservice.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.topic.user-log}")
    private String userLogTopic;

    @Bean
    public NewTopic userLogTopic() {
        return TopicBuilder.name(userLogTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
