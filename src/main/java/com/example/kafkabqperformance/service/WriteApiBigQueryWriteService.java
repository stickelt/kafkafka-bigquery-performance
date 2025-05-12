package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.storage.v1.*;
import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Service("writeApiBigQueryWriteService")
@Slf4j
public class WriteApiBigQueryWriteService implements BigQueryWriteService {

    private final BigQuery bigQuery;
    private final String projectId;
    private final String datasetName;
    private final String tableName;
    private final BigQueryWriteClient writeClient;
    private WriteStream writeStream;
    private JsonStreamWriter streamWriter;
    private final List<String> pendingMessages = new ArrayList<>();
    private final AtomicInteger pendingRowCount = new AtomicInteger(0);

    @Autowired
    public WriteApiBigQueryWriteService(
            BigQuery bigQuery,
            @Value("${bigquery.project-id}") String projectId,
            @Value("${bigquery.dataset}") String datasetName,
            @Value("${bigquery.table}") String tableName) throws IOException, Descriptors.DescriptorValidationException, InterruptedException {
        this.bigQuery = bigQuery;
        this.projectId = projectId;
        this.datasetName = datasetName;
        this.tableName = tableName;
        this.writeClient = BigQueryWriteClient.create();
        
        // Initialize the write stream
        initializeWriteStream();
    }

    private void initializeWriteStream() throws IOException, Descriptors.DescriptorValidationException, InterruptedException {
        TableName parentTable = TableName.of(projectId, datasetName, tableName);
        
        // Create a write stream for the specified table
        writeStream = writeClient.createWriteStream(
                CreateWriteStreamRequest.newBuilder()
                        .setParent(parentTable.toString())
                        .setWriteStream(WriteStream.newBuilder().setType(WriteStream.Type.COMMITTED).build())
                        .build()
        );
        
        // Create a JSON stream writer
        streamWriter = JsonStreamWriter.newBuilder(
                writeStream.getName(), writeStream.getTableSchema()).build();
        
        log.info("Initialized BigQuery Storage Write API stream for {}.{}", datasetName, tableName);
    }

    @Override
    public int writeToBigQuery(List<KafkaMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        for (KafkaMessage message : messages) {
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{")
                    .append("\"id\":\"").append(message.getId()).append("\",")
                    .append("\"message\":\"").append(message.getMessage().replace("\"", "\\\"")).append("\",")
                    .append("\"timestamp\":\"").append(message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString()).append("\",")
                    .append("\"source\":\"").append(message.getSource()).append("\",")
                    .append("\"priority\":").append(message.getPriority()).append(",")
                    .append("\"insert_time\":\"").append(Instant.now().toString()).append("\"")
                    .append("}");
            
            pendingMessages.add(jsonBuilder.toString());
        }
        
        pendingRowCount.addAndGet(messages.size());
        log.debug("Added {} messages to pending rows. Total pending: {}", messages.size(), pendingRowCount.get());
        
        return messages.size();
    }

    @Override
    public int flush() {
        if (pendingMessages.isEmpty()) {
            return 0;
        }
        
        try {
            // Append the JSON data to the stream
            ApiFuture<AppendRowsResponse> future = streamWriter.append(pendingMessages);
            
            // Wait for the append operation to complete
            AppendRowsResponse response = future.get();
            
            int successCount = pendingRowCount.get();
            log.info("BigQuery Storage Write API: Flushed {} records successfully. Offset: {}", 
                    successCount, response.getAppendResult().getOffset().getValue());
            
            pendingMessages.clear();
            pendingRowCount.set(0);
            
            return successCount;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error flushing records to BigQuery using Storage Write API", e);
            return 0;
        }
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            if (streamWriter != null) {
                streamWriter.close();
            }
            if (writeClient != null) {
                writeClient.close();
            }
        } catch (Exception e) {
            log.error("Error closing BigQuery Storage Write API resources", e);
        }
    }
}
