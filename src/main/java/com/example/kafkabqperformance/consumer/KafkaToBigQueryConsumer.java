package com.example.kafkabqperformance.consumer;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.example.kafkabqperformance.monitoring.PerformanceMonitor;
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
    private final PerformanceMonitor performanceMonitor;
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
            PerformanceMonitor performanceMonitor,
            @Value("${performance.batch-size}") int batchSize) {
        this.objectMapper = objectMapper;
        this.legacyBigQueryWriteService = legacyBigQueryWriteService;
        this.writeApiBigQueryWriteService = writeApiBigQueryWriteService;
        this.performanceMonitor = performanceMonitor;
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
                processBatch(legacyQueue, legacyBigQueryWriteService, "Legacy WriteAll", legacyProcessed);
            }
            
            if (writeApiQueue.size() >= batchSize) {
                processBatch(writeApiQueue, writeApiBigQueryWriteService, "Write API", writeApiProcessed);
            }
            
        } catch (JsonProcessingException e) {
            log.error("Error deserializing Kafka message: {}", message, e);
        }
    }
    
    private void processBatch(ConcurrentLinkedQueue<KafkaMessage> queue, 
                             BigQueryWriteService service, 
                             String serviceName,
                             AtomicInteger counter) {
        List<KafkaMessage> batch = new ArrayList<>(batchSize);
        KafkaMessage message;
        
        // Drain up to batchSize messages from the queue
        while (batch.size() < batchSize && (message = queue.poll()) != null) {
            batch.add(message);
        }
        
        if (!batch.isEmpty()) {
            long startTime = System.currentTimeMillis();
            int written = service.writeToBigQuery(batch);
            int flushed = service.flush();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            counter.addAndGet(flushed);
            
            // Record performance metrics
            boolean isLegacy = serviceName.contains("Legacy");
            performanceMonitor.recordBatchPerformance(isLegacy, flushed, duration);
            
            log.info("{}: Processed batch of {} messages in {} ms. Total processed: {}", 
                    serviceName, flushed, duration, counter.get());
        }
    }
    
    @Scheduled(fixedDelayString = "${performance.flush-interval-ms}")
    public void scheduledFlush() {
        // Flush any remaining messages in the queues
        if (!legacyQueue.isEmpty()) {
            processBatch(legacyQueue, legacyBigQueryWriteService, "Legacy WriteAll (scheduled)", legacyProcessed);
        }
        
        if (!writeApiQueue.isEmpty()) {
            processBatch(writeApiQueue, writeApiBigQueryWriteService, "Write API (scheduled)", writeApiProcessed);
        }
    }
}
