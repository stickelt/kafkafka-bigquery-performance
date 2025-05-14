package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessageRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BigQueryQueryServiceImpl implements BigQueryQueryService {

    private final BigQuery bigQuery;
    private final String projectId;
    private final String datasetName;
    private final String tableName;
    private final ObjectMapper objectMapper;

    @Autowired
    public BigQueryQueryServiceImpl(
            BigQuery bigQuery,
            ObjectMapper objectMapper,
            @Value("${bigquery.project-id}") String projectId,
            @Value("${bigquery.dataset}") String datasetName,
            @Value("${bigquery.table}") String tableName) {
        this.bigQuery = bigQuery;
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.datasetName = datasetName;
        this.tableName = tableName;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String query) {
        try {
            log.debug("Executing query: {}", query);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult results = bigQuery.query(queryConfig);
            
            return convertTableResultToList(results);
        } catch (InterruptedException e) {
            log.error("Query execution was interrupted", e);
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> getRecentMessages(int limit) {
        String fullyQualifiedTableName = String.format("`%s.%s.%s`", projectId, datasetName, tableName);
        String query = String.format(
                "SELECT * FROM %s ORDER BY received_timestamp DESC LIMIT %d",
                fullyQualifiedTableName, limit);
        
        return executeQuery(query);
    }

    @Override
    public List<Map<String, Object>> getMessageCountBySource() {
        String fullyQualifiedTableName = String.format("`%s.%s.%s`", projectId, datasetName, tableName);
        String query = String.format(
                "SELECT COUNT(*) as count FROM %s GROUP BY http_status_code ORDER BY count DESC",
                fullyQualifiedTableName);
        
        return executeQuery(query);
    }
    
    @Override
    public List<KafkaMessageRecord> getRecentKafkaMessages(int limit) {
        List<Map<String, Object>> results = getRecentMessages(limit);
        return convertToKafkaMessageRecords(results);
    }
    
    @Override
    public List<KafkaMessageRecord> getMessagesByStatusCode(int statusCode, int limit) {
        String fullyQualifiedTableName = String.format("`%s.%s.%s`", projectId, datasetName, tableName);
        String query = String.format(
                "SELECT * FROM %s WHERE http_status_code = %d ORDER BY received_timestamp DESC LIMIT %d",
                fullyQualifiedTableName, statusCode, limit);
        
        List<Map<String, Object>> results = executeQuery(query);
        return convertToKafkaMessageRecords(results);
    }
    
    @Override
    public List<KafkaMessageRecord> getMessagesWithErrors(int limit) {
        String fullyQualifiedTableName = String.format("`%s.%s.%s`", projectId, datasetName, tableName);
        String query = String.format(
                "SELECT * FROM %s WHERE ARRAY_LENGTH(api_response.errors) > 0 ORDER BY received_timestamp DESC LIMIT %d",
                fullyQualifiedTableName, limit);
        
        List<Map<String, Object>> results = executeQuery(query);
        return convertToKafkaMessageRecords(results);
    }
    
    @Override
    public boolean insertOrUpdateRecord(KafkaMessageRecord record) {
        try {
            TableId tableId = TableId.of(projectId, datasetName, tableName);
            
            // Convert KafkaMessageRecord to a map
            Map<String, Object> rowContent = objectMapper.convertValue(record, Map.class);
            
            // Build the row to insert
            InsertAllRequest.Builder builder = InsertAllRequest.newBuilder(tableId);
            builder.addRow(rowContent);
            
            // Execute the insert
            InsertAllResponse response = bigQuery.insertAll(builder.build());
            
            if (response.hasErrors()) {
                // Log errors
                response.getInsertErrors().forEach((index, errors) -> {
                    errors.forEach(error -> log.error("Error inserting row: {}", error.getMessage()));
                });
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error inserting record", e);
            return false;
        }
    }

    private List<KafkaMessageRecord> convertToKafkaMessageRecords(List<Map<String, Object>> maps) {
        return maps.stream()
                .map(map -> {
                    try {
                        return objectMapper.convertValue(map, KafkaMessageRecord.class);
                    } catch (Exception e) {
                        log.error("Error converting map to KafkaMessageRecord", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> convertTableResultToList(TableResult result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        
        for (FieldValueList fieldValues : result.iterateAll()) {
            Map<String, Object> row = new HashMap<>();
            
            // Process each field in the schema
            for (Field field : result.getSchema().getFields()) {
                String fieldName = field.getName();
                FieldValue value = fieldValues.get(fieldName);
                
                if (value == null || value.isNull()) {
                    row.put(fieldName, null);
                } else {
                    StandardSQLTypeName typeEnum = field.getType().getStandardType();
                    switch (typeEnum) {
                        case STRING:
                            row.put(fieldName, value.getStringValue());
                            break;
                        case BOOL:
                            row.put(fieldName, value.getBooleanValue());
                            break;
                        case INT64:
                            row.put(fieldName, value.getLongValue());
                            break;
                        case FLOAT64:
                            row.put(fieldName, value.getDoubleValue());
                            break;
                        case TIMESTAMP:
                            row.put(fieldName, Instant.ofEpochMilli(value.getTimestampValue() / 1000));
                            break;
                        case STRUCT:
                            row.put(fieldName, processRecord(field, value));
                            break;
                        case ARRAY:
                            row.put(fieldName, processArray(field, value));
                            break;
                        default:
                            row.put(fieldName, value.getValue());
                    }
                }
            }
            
            rows.add(row);
        }
        
        return rows;
    }
    
    private Map<String, Object> processRecord(Field field, FieldValue value) {
        Map<String, Object> record = new HashMap<>();
        
        FieldList subFields = field.getSubFields();
        FieldValueList subFieldValues = value.getRecordValue();
        
        for (int i = 0; i < subFields.size(); i++) {
            Field subField = subFields.get(i);
            FieldValue subValue = subFieldValues.get(i);
            
            if (subValue.isNull()) {
                record.put(subField.getName(), null);
                continue;
            }
            
            // Handle sub-record fields based on their types
            StandardSQLTypeName typeEnum = subField.getType().getStandardType();
            switch (typeEnum) {
                case STRING:
                    record.put(subField.getName(), subValue.getStringValue());
                    break;
                case BOOL:
                    record.put(subField.getName(), subValue.getBooleanValue());
                    break;
                case INT64:
                    record.put(subField.getName(), subValue.getLongValue());
                    break;
                case FLOAT64:
                    record.put(subField.getName(), subValue.getDoubleValue());
                    break;
                case TIMESTAMP:
                    record.put(subField.getName(), Instant.ofEpochMilli(subValue.getTimestampValue() / 1000));
                    break;
                case STRUCT:
                    record.put(subField.getName(), processRecord(subField, subValue));
                    break;
                case ARRAY:
                    record.put(subField.getName(), processArray(subField, subValue));
                    break;
                default:
                    record.put(subField.getName(), subValue.getValue());
            }
        }
        
        return record;
    }
    
    private List<Object> processArray(Field field, FieldValue value) {
        List<Object> array = new ArrayList<>();
        
        // Get repeated values from the array
        List<FieldValue> arrayValues = new ArrayList<>();
        try {
            // Use getRepeatedValue() to get array values
            arrayValues = value.getRepeatedValue();
        } catch (Exception e) {
            log.error("Error getting array values: {}", e.getMessage());
            return array;
        }
        
        for (FieldValue arrayValue : arrayValues) {
            if (arrayValue.isNull()) {
                array.add(null);
                continue;
            }
            
            // Handle array element based on the field type
            Field subField = field.getSubFields() != null && !field.getSubFields().isEmpty() ? field.getSubFields().get(0) : field;
            try {
                StandardSQLTypeName typeEnum = subField.getType().getStandardType();
                switch (typeEnum) {
                    case STRING:
                        array.add(arrayValue.getStringValue());
                        break;
                    case BOOL:
                        array.add(arrayValue.getBooleanValue());
                        break;
                    case INT64:
                        array.add(arrayValue.getLongValue());
                        break;
                    case FLOAT64:
                        array.add(arrayValue.getDoubleValue());
                        break;
                    case TIMESTAMP:
                        array.add(Instant.ofEpochMilli(arrayValue.getTimestampValue() / 1000));
                        break;
                    case STRUCT:
                        array.add(processRecord(subField, arrayValue));
                        break;
                    default:
                        array.add(arrayValue.getValue());
                }
            } catch (Exception e) {
                log.error("Error processing array element: {}", e.getMessage());
                array.add(null);
            }
        }
        
        return array;
    }
} 