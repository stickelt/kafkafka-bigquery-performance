package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of BigQueryWriteService that doesn't connect to BigQuery
 * This is used for testing without BigQuery
 */
@Service
@Profile("nobq")
@Slf4j
public class MockBigQueryWriteService implements BigQueryWriteService {

    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicInteger flushCount = new AtomicInteger(0);

    public MockBigQueryWriteService() {
        log.info("Initialized Mock BigQuery service - no actual BigQuery connections will be made");
    }

    @Override
    public int writeToBigQuery(List<KafkaMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        
        int count = messages.size();
        int totalCount = messageCount.addAndGet(count);
        
        log.debug("Mock BigQuery: Added {} messages. Total: {}", count, totalCount);
        
        return count;
    }

    @Override
    public int flush() {
        int count = messageCount.getAndSet(0);
        int totalFlushes = flushCount.incrementAndGet();
        
        log.info("Mock BigQuery: Flushed {} messages. Total flushes: {}", count, totalFlushes);
        
        return count;
    }
}
