package com.example.kafkabqperformance.controller;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.example.kafkabqperformance.model.Message;
import com.example.kafkabqperformance.model.TestRequest;
import com.example.kafkabqperformance.model.TestResponse;
import com.example.kafkabqperformance.service.BigQueryWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for testing BigQuery directly without Kafka
 * This controller allows us to test BigQuery performance without requiring a Kafka broker
 */
@RestController
@RequestMapping("/api/direct")
public class DirectBigQueryController {
    private static final Logger logger = LoggerFactory.getLogger(DirectBigQueryController.class);

    private final BigQueryWriteService legacyBigQueryWriteService;
    private final BigQueryWriteService writeApiBigQueryWriteService;

    public DirectBigQueryController(
            @Qualifier("legacyBigQueryWriteService") BigQueryWriteService legacyBigQueryWriteService,
            @Qualifier("writeApiBigQueryWriteService") BigQueryWriteService writeApiBigQueryWriteService) {
        this.legacyBigQueryWriteService = legacyBigQueryWriteService;
        this.writeApiBigQueryWriteService = writeApiBigQueryWriteService;
    }

    @PostMapping("/test-legacy")
    public ResponseEntity<TestResponse> testLegacy(@RequestBody TestRequest request) {
        logger.info("Received direct test-legacy request: {}", request);
        
        long startTime = System.currentTimeMillis();
        List<Message> messages = generateMessages(request.getMessageCount());
        
        // Process messages in batches
        int batchSize = request.getBatchSize();
        for (int i = 0; i < messages.size(); i += batchSize) {
            int end = Math.min(i + batchSize, messages.size());
            List<Message> batch = messages.subList(i, end);
            
            // Convert Message to KafkaMessage
            List<KafkaMessage> kafkaMessages = convertToKafkaMessages(batch);
            legacyBigQueryWriteService.writeToBigQuery(kafkaMessages);
        }
        
        // Flush to ensure data is sent to BigQuery
        int recordsWritten = legacyBigQueryWriteService.flush();
        logger.info("Legacy API: Flushed {} records to BigQuery", recordsWritten);
        
        long duration = System.currentTimeMillis() - startTime;
        
        TestResponse response = new TestResponse(
                request.getMessageCount(),
                request.getBatchSize(),
                duration,
                String.format("Direct Legacy API test completed successfully. %d records written to BigQuery", recordsWritten)
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test-write-api")
    public ResponseEntity<TestResponse> testWriteApi(@RequestBody TestRequest request) {
        logger.info("Received direct test-write-api request: {}", request);
        
        long startTime = System.currentTimeMillis();
        List<Message> messages = generateMessages(request.getMessageCount());
        
        // Process messages in batches
        int batchSize = request.getBatchSize();
        for (int i = 0; i < messages.size(); i += batchSize) {
            int end = Math.min(i + batchSize, messages.size());
            List<Message> batch = messages.subList(i, end);
            
            // Convert Message to KafkaMessage
            List<KafkaMessage> kafkaMessages = convertToKafkaMessages(batch);
            writeApiBigQueryWriteService.writeToBigQuery(kafkaMessages);
        }
        
        // Flush to ensure data is sent to BigQuery
        int recordsWritten = writeApiBigQueryWriteService.flush();
        logger.info("Storage Write API: Flushed {} records to BigQuery", recordsWritten);
        
        long duration = System.currentTimeMillis() - startTime;
        
        TestResponse response = new TestResponse(
                request.getMessageCount(),
                request.getBatchSize(),
                duration,
                String.format("Direct Storage Write API test completed successfully. %d records written to BigQuery", recordsWritten)
        );
        
        return ResponseEntity.ok(response);
    }

    private List<Message> generateMessages(int count) {
        List<Message> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Message message = new Message(
                    UUID.randomUUID().toString(),
                    "Test message " + i,
                    Instant.now(),
                    "direct-test",
                    (i % 3) + 1
            );
            messages.add(message);
        }
        return messages;
    }
    
    private List<KafkaMessage> convertToKafkaMessages(List<Message> messages) {
        return messages.stream()
                .map(message -> {
                    KafkaMessage kafkaMessage = new KafkaMessage();
                    kafkaMessage.setId(message.getId());
                    kafkaMessage.setMessage(message.getContent());
                    kafkaMessage.setTimestamp(message.getTimestamp());
                    kafkaMessage.setSource(message.getTopic());
                    kafkaMessage.setPriority(message.getPartition());
                    return kafkaMessage;
                })
                .collect(Collectors.toList());
    }
} 