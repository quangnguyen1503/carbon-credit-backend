package com.example.carbon_credit.Config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name("orders").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic tradesTopic() {
        return TopicBuilder.name("trades").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic eventsTopic() {
        return TopicBuilder.name("onchain-events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic marketDataTopic() {return TopicBuilder.name("market-data").partitions(3).replicas(1).build();};

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        // 1. Set ConsumerFactory (tự động lấy từ application.properties)
        factory.setConsumerFactory(consumerFactory);

        // 2. QUAN TRỌNG: Bật chế độ Manual Acknowledgment
        // Sửa lỗi "No Acknowledgment available as an argument"
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 3. Cấu hình Concurrency mặc định (số luồng consumer)
        // Bạn có thể override trong @KafkaListener(concurrency = "...")
        factory.setConcurrency(3);

        // 4. Cấu hình Error Handler (Khuyên dùng)
        // Nếu xử lý message bị lỗi, retry 2 lần (cách nhau 1s). Nếu vẫn lỗi thì log và bỏ qua.
        // Giúp consumer không bị kẹt vô tận tại 1 message lỗi.
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 2L)
        ));

        return factory;
    }
}

