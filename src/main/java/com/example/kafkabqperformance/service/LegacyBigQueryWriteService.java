package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.google.cloud.bigquery.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BigQuery write service implementation using the Legacy insertAll API
 * 
 * This service provides buffered writing to BigQuery using the standard Legacy API.
 * It includes auto-flush when a threshold is reached and retry logic for failed insertions.
 */
@Service("legacyBigQueryWriteService")
@Slf4j
public class LegacyBigQueryWriteService implements BigQueryWriteService {

    private final BigQuery bigQuery;
    private final String datasetName;
    private final String tableName;
    private final List<InsertAllRequest.RowToInsert> pendingRows = new ArrayList<>();
    private final AtomicInteger pendingRowCount = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    /**
     * Maximum number of rows to buffer before auto-flushing to BigQuery
     * This helps optimize batch sizes while preventing excessive memory consumption
     */
    private final int flushThreshold;
    
    /**
     * Maximum number of retry attempts for failed insertions
     */
    private final int maxRetryAttempts;

    @Autowired
    public LegacyBigQueryWriteService(
            BigQuery bigQuery,
            @Value("${bigquery.dataset}") String datasetName,
            @Value("${bigquery.table}") String tableName,
            @Value("${bigquery.flush-threshold:500}") int flushThreshold,
            @Value("${bigquery.max-retry-attempts:3}") int maxRetryAttempts) {
        this.bigQuery = bigQuery;
        this.datasetName = datasetName;
        this.tableName = tableName;
        this.flushThreshold = flushThreshold;
        this.maxRetryAttempts = maxRetryAttempts;
        
        log.info("Initialized Legacy BigQuery service with flush threshold: {}, max retry attempts: {}", 
                flushThreshold, maxRetryAttempts);
    }

    @Override
    public int writeToBigQuery(List<KafkaMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        TableId tableId = TableId.of(datasetName, tableName);
        
        for (KafkaMessage message : messages) {
            Map<String, Object> rowContent = new HashMap<>();
            // Map to the actual BigQuery schema
            rowContent.put("uuid", message.getId());
            rowContent.put("received_timestamp", Instant.now().toString());
            rowContent.put("raw_payload", message.getMessage());
            rowContent.put("processing_timestamp", Instant.now().toString());
            rowContent.put("http_status_code", 200); // Default success
            
            // Create the nested api_response record
            Map<String, Object> apiResponse = new HashMap<>();
            apiResponse.put("rx_data_id", message.getPriority() != null ? message.getPriority() : 0);
            apiResponse.put("errors", new ArrayList<String>());
            apiResponse.put("submitted_date", message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString());
            apiResponse.put("process_date", Instant.now().toString());
            apiResponse.put("aspn_id", 1000); // Default value
            
            rowContent.put("api_response", apiResponse);
            rowContent.put("submitted_date", message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString());
            rowContent.put("process_date", Instant.now().toString());
            rowContent.put("aspn_id", 1000); // Default value
            rowContent.put("rx_data_id", message.getPriority() != null ? message.getPriority() : 0);
            
            InsertAllRequest.RowToInsert row = InsertAllRequest.RowToInsert.of(message.getId(), rowContent);
            pendingRows.add(row);
            
            log.debug("Mapped message to BigQuery schema: {}", rowContent);
        }
        
        int currentCount = pendingRowCount.addAndGet(messages.size());
        log.debug("Added {} messages to pending rows. Current total: {}", messages.size(), currentCount);
        
        // Auto-flush when threshold is reached
        if (currentCount >= flushThreshold) {
            log.info("Auto-flush triggered: pending count ({}) exceeded threshold ({})", 
                    currentCount, flushThreshold);
            flushAsync();
        }
        
        return messages.size();
    }

    @Override
    public int flush() {
        if (pendingRows.isEmpty()) {
            return 0;
        }
        
        return flushWithRetry(0);
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
    
    /**
     * Recursive method to flush pending rows with retry logic
     * 
     * @param attemptCount Current attempt number (0-based)
     * @return Number of successfully written rows
     */
    private int flushWithRetry(int attemptCount) {
        if (pendingRows.isEmpty()) {
            return 0;
        }
        
        TableId tableId = TableId.of(datasetName, tableName);
        InsertAllRequest insertRequest = InsertAllRequest.newBuilder(tableId)
                .setRows(pendingRows)
                .build();
        
        InsertAllResponse response = bigQuery.insertAll(insertRequest);
        int failedRowCount = response.getInsertErrors().size();
        int successCount = pendingRowCount.get() - failedRowCount;
        
        // If there are errors but we have retries left, attempt to retry failed rows
        if (response.hasErrors()) {
            log.warn("Errors occurred while inserting rows (attempt {}): {} failures", 
                    attemptCount + 1, failedRowCount);
            
            if (attemptCount < maxRetryAttempts) {
                return retryFailedRows(response.getInsertErrors(), attemptCount + 1);
            } else {
                log.error("Max retry attempts ({}) reached. Error details: {}", 
                        maxRetryAttempts, response.getInsertErrors());
            }
        }
        
        log.info("Legacy BigQuery WriteAll: Flushed {} records, {} successful, {} failed", 
                pendingRowCount.get(), successCount, failedRowCount);
        
        // Clear pending rows only after final attempt
        pendingRows.clear();
        pendingRowCount.set(0);
        
        return successCount;
    }
    
    /**
     * Retry insertion of failed rows
     * 
     * @param insertErrors Map of row indices to error information from failed insertion
     * @param attemptCount Current attempt number (1-based)
     * @return Number of successfully written rows after retry
     */
    private int retryFailedRows(Map<Long, List<BigQueryError>> insertErrors, int attemptCount) {
        List<InsertAllRequest.RowToInsert> rowsToRetry = new ArrayList<>();
        List<InsertAllRequest.RowToInsert> originalRows = new ArrayList<>(pendingRows);
        
        // Clear the current pending rows
        pendingRows.clear();
        
        // Add back only the rows that failed with retriable errors
        for (Map.Entry<Long, List<BigQueryError>> entry : insertErrors.entrySet()) {
            int rowIndex = entry.getKey().intValue(); // Convert Long to int
            List<BigQueryError> errors = entry.getValue();
            
            if (isRetriableError(errors)) {
                InsertAllRequest.RowToInsert failedRow = originalRows.get(rowIndex);
                rowsToRetry.add(failedRow);
                pendingRows.add(failedRow);
                
                log.debug("Retrying row with id {}, error: {}", 
                        failedRow.getId(), errors.get(0).getMessage());
            } else {
                log.error("Permanent error for row {}, skipping retry: {}", 
                        rowIndex, errors.get(0).getMessage());
            }
        }
        
        int retriedRowCount = rowsToRetry.size();
        pendingRowCount.set(retriedRowCount);
        
        if (retriedRowCount > 0) {
            log.info("Retry attempt {}: Retrying {} rows with retriable errors", 
                    attemptCount, retriedRowCount);
            
            // Add a small backoff before retry (exponential backoff strategy)
            try {
                Thread.sleep(Math.min(100 * (1 << attemptCount), 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return flushWithRetry(attemptCount);
        }
        
        // If no rows to retry, return the number of successful rows from the first attempt
        return pendingRowCount.get() - insertErrors.size();
    }
    
    /**
     * Determine if errors for a row are retriable based on the error reason
     * 
     * @param errors List of BigQuery errors for a particular row
     * @return true if the errors are retriable, false otherwise
     */
    private boolean isRetriableError(List<BigQueryError> errors) {
        if (errors == null || errors.isEmpty()) {
            return false;
        }
        
        // Get the first error (most relevant)
        BigQueryError error = errors.get(0);
        String reason = error.getReason();
        String message = error.getMessage();
        
        // Common retriable errors
        return "backendError".equals(reason) || 
               "rateLimitExceeded".equals(reason) || 
               "internalError".equals(reason) || 
               message.contains("timeout") || 
               message.contains("temporarily unavailable");
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down Legacy BigQuery service and executor");
        executor.shutdown();
    }
}
