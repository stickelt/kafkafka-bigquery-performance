package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.storage.v1.*;
import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
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
    private final ObjectMapper objectMapper;

    @Autowired
    public WriteApiBigQueryWriteService(
            BigQuery bigQuery,
            ObjectMapper objectMapper,
            @Value("${bigquery.project-id}") String projectId,
            @Value("${bigquery.dataset}") String datasetName,
            @Value("${bigquery.table}") String tableName) throws IOException, Descriptors.DescriptorValidationException, InterruptedException {
        this.bigQuery = bigQuery;
        this.objectMapper = objectMapper;
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

        try {
            for (KafkaMessage message : messages) {
                // Create JSON using ObjectMapper for proper escaping and formatting
                ObjectNode jsonNode = objectMapper.createObjectNode();
                jsonNode.put("uuid", message.getId());
                jsonNode.put("received_timestamp", Instant.now().toString());
                jsonNode.put("raw_payload", message.getMessage());
                jsonNode.put("processing_timestamp", Instant.now().toString());
                jsonNode.put("http_status_code", 200);
                
                // Create the nested api_response object
                ObjectNode apiResponse = objectMapper.createObjectNode();
                apiResponse.put("rx_data_id", message.getPriority() != null ? message.getPriority() : 0);
                apiResponse.putArray("errors"); // Empty array
                apiResponse.put("submitted_date", message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString());
                apiResponse.put("process_date", Instant.now().toString());
                apiResponse.put("aspn_id", 1000);
                
                jsonNode.set("api_response", apiResponse);
                jsonNode.put("submitted_date", message.getTimestamp() != null ? message.getTimestamp().toString() : Instant.now().toString());
                jsonNode.put("process_date", Instant.now().toString());
                jsonNode.put("aspn_id", 1000);
                jsonNode.put("rx_data_id", message.getPriority() != null ? message.getPriority() : 0);
                
                // Convert to JSON string
                String jsonString = objectMapper.writeValueAsString(jsonNode);
                pendingMessages.add(jsonString);
                
                log.debug("Mapped message to BigQuery schema: {}", jsonString);
            }
            
            pendingRowCount.addAndGet(messages.size());
            log.debug("Added {} messages to pending rows. Total pending: {}", messages.size(), pendingRowCount.get());
            
            return messages.size();
        } catch (JsonProcessingException e) {
            log.error("Error converting messages to JSON", e);
            return 0;
        }
    }

    @Override
    public int flush() {
        if (pendingMessages.isEmpty()) {
            return 0;
        }
        
        try {
            // Convert List<String> to JSONArray before passing to append
            JSONArray jsonArray = new JSONArray();
            for (String message : pendingMessages) {
                jsonArray.put(new JSONObject(message));
            }
            
            // Append the JSON data to the stream
            ApiFuture<AppendRowsResponse> future = streamWriter.append(jsonArray);
            
            // Wait for the append operation to complete
            AppendRowsResponse response = future.get();
            
            int successCount = pendingRowCount.get();
            log.info("BigQuery Storage Write API: Flushed {} records successfully. Offset: {}", 
                    successCount, response.getAppendResult().getOffset().getValue());
            
            pendingMessages.clear();
            pendingRowCount.set(0);
            
            return successCount;
        } catch (ExecutionException | InterruptedException | IOException | Descriptors.DescriptorValidationException e) {
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
