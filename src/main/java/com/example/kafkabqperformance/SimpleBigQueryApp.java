package com.example.kafkabqperformance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * A simplified version of the application that excludes Kafka and BigQuery components
 */
@SpringBootApplication(exclude = {KafkaAutoConfiguration.class})
@ComponentScan(
    basePackages = "com.example.kafkabqperformance",
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.example\\.kafkabqperformance\\.service\\.(WriteApiBigQueryWriteService|LegacyBigQueryWriteService)"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.example\\.kafkabqperformance\\.consumer\\.KafkaToBigQueryConsumer"
        )
    }
)
public class SimpleBigQueryApp {

    public static void main(String[] args) {
        SpringApplication.run(SimpleBigQueryApp.class, args);
    }
}
