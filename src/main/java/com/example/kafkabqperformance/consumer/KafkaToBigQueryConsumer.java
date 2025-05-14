package com.example.kafkabqperformance.consumer;

import com.example.kafkabqperformance.model.KafkaMessage;
// import com.example.kafkabqperformance.monitoring.PerformanceMonitor;
import com.example.kafkabqperformance.service.BigQueryWriteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class KafkaToBigQueryConsumer {

    private final ObjectMapper objectMapper;
    private final BigQueryWriteService legacyBigQueryWriteService;
    private final BigQueryWriteService writeApiBigQueryWriteService;
    // private final PerformanceMonitor performanceMonitor;
    private final int batchSize;
    
    private final ConcurrentLinkedQueue<KafkaMessage> legacyQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<KafkaMessage> writeApiQueue = new ConcurrentLinkedQueue<>();
    
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger legacyProcessed = new AtomicInteger(0);
    private final AtomicInteger writeApiProcessed = new AtomicInteger(0);
    
    @Autowired
    public KafkaToBigQueryConsumer(
            ObjectMapper objectMapper,
            @Qualifier("legacyBigQueryWriteService") BigQueryWriteService legacyBigQueryWriteService,
            @Qualifier("writeApiBigQueryWriteService") BigQueryWriteService writeApiBigQueryWriteService,
            // PerformanceMonitor performanceMonitor,
            @Value("${performance.batch-size}") int batchSize) {
        this.objectMapper = objectMapper;
        this.legacyBigQueryWriteService = legacyBigQueryWriteService;
        this.writeApiBigQueryWriteService = writeApiBigQueryWriteService;
        // this.performanceMonitor = performanceMonitor;
        this.batchSize = batchSize;
    }

    @KafkaListener(topics = "${kafka.topic}")
    public void consume(String message) {
        try {
            KafkaMessage kafkaMessage = objectMapper.readValue(message, KafkaMessage.class);
            
            // Add to both queues to compare performance
            legacyQueue.add(kafkaMessage);
            writeApiQueue.add(kafkaMessage);
            
            int processed = totalProcessed.incrementAndGet();
            if (processed % 1000 == 0) {
                log.info("Processed {} messages", processed);
            }
            
            // Process in batches if we've reached the batch size
            if (legacyQueue.size() >= batchSize) {
                processBatch(legacyQueue, legacyBigQueryWriteService, true);
            }
            
            if (writeApiQueue.size() >= batchSize) {
                processBatch(writeApiQueue, writeApiBigQueryWriteService, false);
            }
            
        } catch (JsonProcessingException e) {
            log.error("Error deserializing Kafka message: {}", message, e);
        }
    }
    
    private void processBatch(ConcurrentLinkedQueue<KafkaMessage> queue, BigQueryWriteService service, boolean isLegacy) {
        if (queue.isEmpty()) {
            return;
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Convert queue to list for processing
            List<KafkaMessage> batch = new ArrayList<>(queue);
            queue.clear(); // Clear the queue after copying to list
            
            service.writeToBigQuery(batch);
            int flushed = service.flush();
            
            long duration = System.currentTimeMillis() - startTime;
            String serviceType = isLegacy ? "Legacy" : "Write API";
            
            log.debug("{}: Processed batch of {} messages in {} ms", serviceType, flushed, duration);
            
            // Record performance metrics
            // performanceMonitor.recordBatchPerformance(isLegacy, flushed, duration);
            
            // Update counters
            if (isLegacy) {
                legacyProcessed.addAndGet(flushed);
            } else {
                writeApiProcessed.addAndGet(flushed);
            }
        } catch (Exception e) {
            log.error("Error processing batch with {}", isLegacy ? "Legacy service" : "Write API service", e);
        }
    }
    
    @Scheduled(fixedDelayString = "${performance.flush-interval-ms}")
    public void scheduledFlush() {
        // Flush any remaining messages in the queues
        if (!legacyQueue.isEmpty()) {
            processBatch(legacyQueue, legacyBigQueryWriteService, true);
        }
        
        if (!writeApiQueue.isEmpty()) {
            processBatch(writeApiQueue, writeApiBigQueryWriteService, false);
        }
    }
}
