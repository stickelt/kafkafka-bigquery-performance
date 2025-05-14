package com.example.kafkabqperformance.controller;

import com.example.kafkabqperformance.model.KafkaMessageRecord;
import com.example.kafkabqperformance.service.BigQueryQueryService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/query")
@Slf4j
public class QueryController {

    private final BigQueryQueryService queryService;

    @Autowired
    public QueryController(BigQueryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/messages")
    public ResponseEntity<List<Map<String, Object>>> getRecentMessages(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting {} most recent messages", limit);
        return ResponseEntity.ok(queryService.getRecentMessages(limit));
    }

    @GetMapping("/counts/by-status")
    public ResponseEntity<List<Map<String, Object>>> getMessageCountsByStatus() {
        log.info("Getting message counts by status");
        return ResponseEntity.ok(queryService.getMessageCountBySource());
    }

    @PostMapping("/execute")
    public ResponseEntity<List<Map<String, Object>>> executeQuery(@RequestBody QueryRequest request) {
        log.info("Executing custom query: {}", request.getQuery());
        return ResponseEntity.ok(queryService.executeQuery(request.getQuery()));
    }
    
    @GetMapping("/kafka-messages")
    public ResponseEntity<List<KafkaMessageRecord>> getRecentKafkaMessages(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting {} most recent kafka messages as objects", limit);
        return ResponseEntity.ok(queryService.getRecentKafkaMessages(limit));
    }
    
    @GetMapping("/status/{statusCode}")
    public ResponseEntity<List<KafkaMessageRecord>> getMessagesByStatusCode(
            @PathVariable int statusCode,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting {} messages with status code {}", limit, statusCode);
        return ResponseEntity.ok(queryService.getMessagesByStatusCode(statusCode, limit));
    }
    
    @GetMapping("/errors")
    public ResponseEntity<List<KafkaMessageRecord>> getMessagesWithErrors(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting {} messages with errors", limit);
        return ResponseEntity.ok(queryService.getMessagesWithErrors(limit));
    }
    
    @PostMapping("/insert")
    public ResponseEntity<String> insertRecord(@RequestBody KafkaMessageRecord record) {
        log.info("Inserting record with UUID: {}", record.getUuid());
        boolean success = queryService.insertOrUpdateRecord(record);
        
        if (success) {
            return ResponseEntity.ok("Record inserted successfully");
        } else {
            return ResponseEntity.badRequest().body("Failed to insert record");
        }
    }

    @Data
    public static class QueryRequest {
        private String query;
    }
} 