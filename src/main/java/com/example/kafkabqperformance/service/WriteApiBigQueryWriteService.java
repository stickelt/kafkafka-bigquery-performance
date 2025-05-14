package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.google.api.core.ApiFuture;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.storage.v1.*;
import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service("writeApiBigQueryWriteService")
@Slf4j
public class WriteApiBigQueryWriteService implements BigQueryWriteService {

    private final BigQuery bigQuery;
    private final String projectId;
    private final String datasetName;
    private final String tableName;
    private final BigQueryWriteClient writeClient;
    private JsonStreamWriter streamWriter;
    private final List<JsonObject> pendingRows = new ArrayList<>();
    private final AtomicInteger pendingRowCount = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final int flushThreshold;

    @Autowired
    public WriteApiBigQueryWriteService(
            BigQuery bigQuery,
            @Value("${bigquery.project-id}") String projectId,
            @Value("${bigquery.dataset}") String datasetName,
            @Value("${bigquery.table}") String tableName,
            @Value("${bigquery.flush-threshold:500}") int flushThreshold) throws IOException, Descriptors.DescriptorValidationException, InterruptedException {
        this.bigQuery = bigQuery;
        this.projectId = projectId;
        this.datasetName = datasetName;
        this.tableName = tableName;
        this.flushThreshold = flushThreshold;
        this.writeClient = BigQueryWriteClient.create();
        
        // Initialize the write stream
        initializeWriteStream();
    }

    private void initializeWriteStream() throws IOException, Descriptors.DescriptorValidationException, InterruptedException {
        try {
            // Format default stream name directly
            String defaultStreamName = String.format("projects/%s/datasets/%s/tables/%s", projectId, datasetName, tableName);
            
            // Get table schema directly from BigQuery
            TableId tableId = TableId.of(datasetName, tableName);
            com.google.cloud.bigquery.Table table = bigQuery.getTable(tableId);
            if (table == null) {
                throw new IOException("Table not found: " + tableId.toString());
            }
            
            // Create stream writer directly with default stream
            streamWriter = JsonStreamWriter.newBuilder(defaultStreamName, table.getDefinition().getSchema()).build();
            
            log.info("Initialized BigQuery Storage Write API stream for {}.{} using default stream", datasetName, tableName);
        } catch (Exception e) {
            log.error("Failed to initialize write stream", e);
            throw e;
        }
    }

    @Override
    public int writeToBigQuery(List<KafkaMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        try {
            for (KafkaMessage message : messages) {
                // Create a JsonObject directly
                JsonObject jsonRow = JsonObject.newBuilder()
                    .put("uuid", message.getId())
                    .put("received_timestamp", Instant.now().toString())
                    .put("raw_payload", message.getMessage())
                    .put("processing_timestamp", Instant.now().toString())
                    .put("http_status_code", 200)
                    .build();
                
                // Create the nested api_response object
                JsonObject apiResponse = JsonObject.newBuilder()
                    .put("rx_data_id", message.getPriority() != null ? message.getPriority() : 0)
                    .put("errors", JsonArray.newBuilder().build()) // Empty array
                    .put("submitted_date", message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString())
                    .put("process_date", Instant.now().toString())
                    .put("aspn_id", 1000)
                    .build();
                
                // Put the api_response as a nested object
                JsonObject finalRow = JsonObject.newBuilder(jsonRow)
                    .put("api_response", apiResponse)
                    .put("submitted_date", message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString())
                    .put("process_date", Instant.now().toString())
                    .put("aspn_id", 1000)
                    .put("rx_data_id", message.getPriority() != null ? message.getPriority() : 0)
                    .build();
                
                pendingRows.add(finalRow);
                log.debug("Mapped message to BigQuery schema JSON");
            }
            
            int currentCount = pendingRowCount.addAndGet(messages.size());
            log.debug("Added {} messages to pending rows. Total pending: {}", messages.size(), currentCount);
            
            // Auto-flush when threshold is reached
            if (currentCount >= flushThreshold) {
                log.info("Auto-flush triggered: pending count ({}) exceeded threshold ({})", 
                        currentCount, flushThreshold);
                flushAsync();
            }
            
            return messages.size();
        } catch (Exception e) {
            log.error("Error converting messages to JSON", e);
            return 0;
        }
    }

    @Override
    public int flush() {
        if (pendingRows.isEmpty()) {
            return 0;
        }
        
        try {
            // Write all pending rows in a single batch
            ApiFuture<AppendRowsResponse> future = streamWriter.append(pendingRows);
            
            // Wait for the append operation to complete
            AppendRowsResponse response = future.get();
            int successCount = pendingRows.size();
            
            log.info("BigQuery Storage Write API: Flushed {} records successfully. Offset: {}", 
                    successCount, response.getAppendResult().getOffset().getValue());
            
            // Clear pending rows and reset count
            pendingRows.clear();
            pendingRowCount.set(0);
            
            return successCount;
        } catch (Exception e) {
            log.error("Error flushing records to BigQuery using Storage Write API", e);
            return 0;
        }
    }
    
    /**
     * Asynchronously flushes the pending rows
     * This prevents blocking the calling thread (e.g., Kafka consumer)
     */
    public void flushAsync() {
        if (!pendingRows.isEmpty()) {
            executor.submit(this::flush);
        }
    }
    
    /**
     * Scheduled periodic flush to handle "stragglers" - records that haven't reached the flush threshold
     * This ensures data is eventually written to BigQuery even with low volume
     */
    @Scheduled(fixedDelayString = "${bigquery.flush-interval-ms:5000}")
    public void scheduledFlush() {
        int pendingCount = pendingRowCount.get();
        if (pendingCount > 0) {
            log.debug("Running scheduled flush for {} pending rows", pendingCount);
            flushAsync();
        }
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            // Flush any remaining records
            flush();
            
            // Close resources
            if (streamWriter != null) {
                streamWriter.close();
            }
            if (writeClient != null) {
                writeClient.close();
            }
            
            // Shutdown executor
            executor.shutdown();
            
            log.info("BigQuery Storage Write API resources closed successfully");
        } catch (Exception e) {
            log.error("Error closing BigQuery Storage Write API resources", e);
        }
    }
}
