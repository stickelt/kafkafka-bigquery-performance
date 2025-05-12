package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.google.cloud.bigquery.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service("legacyBigQueryWriteService")
@Slf4j
public class LegacyBigQueryWriteService implements BigQueryWriteService {

    private final BigQuery bigQuery;
    private final String datasetName;
    private final String tableName;
    private final List<InsertAllRequest.RowToInsert> pendingRows = new ArrayList<>();
    private final AtomicInteger pendingRowCount = new AtomicInteger(0);

    @Autowired
    public LegacyBigQueryWriteService(
            BigQuery bigQuery,
            @Value("${bigquery.dataset}") String datasetName,
            @Value("${bigquery.table}") String tableName) {
        this.bigQuery = bigQuery;
        this.datasetName = datasetName;
        this.tableName = tableName;
    }

    @Override
    public int writeToBigQuery(List<KafkaMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        TableId tableId = TableId.of(datasetName, tableName);
        
        for (KafkaMessage message : messages) {
            Map<String, Object> rowContent = new HashMap<>();
            rowContent.put("id", message.getId());
            rowContent.put("message", message.getMessage());
            rowContent.put("timestamp", message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString());
            rowContent.put("source", message.getSource());
            rowContent.put("priority", message.getPriority());
            rowContent.put("insert_time", Instant.now().toString());
            
            InsertAllRequest.RowToInsert row = InsertAllRequest.RowToInsert.of(message.getId(), rowContent);
            pendingRows.add(row);
        }
        
        pendingRowCount.addAndGet(messages.size());
        log.debug("Added {} messages to pending rows. Total pending: {}", messages.size(), pendingRowCount.get());
        
        return messages.size();
    }

    @Override
    public int flush() {
        if (pendingRows.isEmpty()) {
            return 0;
        }
        
        TableId tableId = TableId.of(datasetName, tableName);
        InsertAllRequest insertRequest = InsertAllRequest.newBuilder(tableId)
                .setRows(pendingRows)
                .build();
        
        InsertAllResponse response = bigQuery.insertAll(insertRequest);
        int successCount = pendingRowCount.get() - response.getInsertErrors().size();
        
        if (response.hasErrors()) {
            log.error("Errors occurred while inserting rows: {}", response.getInsertErrors());
        }
        
        log.info("Legacy BigQuery WriteAll: Flushed {} records, {} successful, {} failed", 
                pendingRowCount.get(), successCount, response.getInsertErrors().size());
        
        pendingRows.clear();
        pendingRowCount.set(0);
        
        return successCount;
    }
}
