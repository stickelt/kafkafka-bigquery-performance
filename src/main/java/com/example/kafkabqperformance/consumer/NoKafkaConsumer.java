package com.example.kafkabqperformance.consumer;

import com.example.kafkabqperformance.service.BigQueryWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * No-op Kafka consumer that doesn't actually connect to Kafka
 * This class is used when running with the 'nokafka' profile
 */
@Component
@Profile("nokafka")
public class NoKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(NoKafkaConsumer.class);
    
    private final BigQueryWriteService legacyBigQueryWriteService;
    private final BigQueryWriteService writeApiBigQueryWriteService;
    
    public NoKafkaConsumer(
            BigQueryWriteService legacyBigQueryWriteService,
            BigQueryWriteService writeApiBigQueryWriteService) {
        this.legacyBigQueryWriteService = legacyBigQueryWriteService;
        this.writeApiBigQueryWriteService = writeApiBigQueryWriteService;
        logger.info("Initialized NoKafkaConsumer - will not connect to Kafka");
    }
} 