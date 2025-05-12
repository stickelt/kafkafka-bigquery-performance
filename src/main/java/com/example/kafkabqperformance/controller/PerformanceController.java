package com.example.kafkabqperformance.controller;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.example.kafkabqperformance.service.BigQueryWriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Slf4j
public class PerformanceController {

    private final BigQueryWriteService legacyBigQueryWriteService;
    private final BigQueryWriteService writeApiBigQueryWriteService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PerformanceController(
            @Qualifier("legacyBigQueryWriteService") BigQueryWriteService legacyBigQueryWriteService,
            @Qualifier("writeApiBigQueryWriteService") BigQueryWriteService writeApiBigQueryWriteService,
            ObjectMapper objectMapper) {
        this.legacyBigQueryWriteService = legacyBigQueryWriteService;
        this.writeApiBigQueryWriteService = writeApiBigQueryWriteService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Service is running");
    }

    @PostMapping("/test-legacy")
    public ResponseEntity<TestResult> testLegacyWriteAll(@RequestBody TestRequest request) {
        return testBigQueryWrite(request, legacyBigQueryWriteService, "Legacy WriteAll");
    }

    @PostMapping("/test-write-api")
    public ResponseEntity<TestResult> testWriteApi(@RequestBody TestRequest request) {
        return testBigQueryWrite(request, writeApiBigQueryWriteService, "Storage Write API");
    }

    private ResponseEntity<TestResult> testBigQueryWrite(
            TestRequest request,
            BigQueryWriteService service,
            String serviceName) {
        
        int messageCount = request.getMessageCount() > 0 ? request.getMessageCount() : 1000;
        int batchSize = request.getBatchSize() > 0 ? request.getBatchSize() : 100;
        
        log.info("Running performance test for {} with {} messages and batch size {}", 
                serviceName, messageCount, batchSize);
        
        List<KafkaMessage> messages = generateTestMessages(messageCount);
        
        long startTime = System.currentTimeMillis();
        
        int totalProcessed = 0;
        for (int i = 0; i < messages.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, messages.size());
            List<KafkaMessage> batch = messages.subList(i, endIndex);
            
            service.writeToBigQuery(batch);
            int flushed = service.flush();
            totalProcessed += flushed;
            
            log.info("{}: Processed batch {} of {} ({} messages)", 
                    serviceName, (i / batchSize) + 1, (int) Math.ceil(messages.size() / (double) batchSize), flushed);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        TestResult result = new TestResult();
        result.setServiceName(serviceName);
        result.setTotalMessages(messageCount);
        result.setProcessedMessages(totalProcessed);
        result.setTotalTimeMs(duration);
        result.setMessagesPerSecond((double) totalProcessed / (duration / 1000.0));
        
        log.info("{} test complete: {} messages in {} ms ({} messages/second)", 
                serviceName, totalProcessed, duration, result.getMessagesPerSecond());
        
        return ResponseEntity.ok(result);
    }
    
    private List<KafkaMessage> generateTestMessages(int count) {
        List<KafkaMessage> messages = new ArrayList<>(count);
        
        for (int i = 0; i < count; i++) {
            KafkaMessage message = new KafkaMessage();
            message.setId(UUID.randomUUID().toString());
            message.setMessage("Test message " + i);
            message.setTimestamp(Instant.now());
            message.setSource("performance-test");
            message.setPriority(i % 3); // Mix of priorities
            
            messages.add(message);
        }
        
        return messages;
    }
    
    @Data
    public static class TestRequest {
        private int messageCount = 1000;
        private int batchSize = 100;
    }
    
    @Data
    public static class TestResult {
        private String serviceName;
        private int totalMessages;
        private int processedMessages;
        private long totalTimeMs;
        private double messagesPerSecond;
    }
}
