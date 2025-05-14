package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessageRecord;

import java.util.List;
import java.util.Map;

/**
 * Service for querying data from BigQuery
 */
public interface BigQueryQueryService {

    /**
     * Execute a query against BigQuery and return the results as a list of maps
     * 
     * @param query The SQL query to execute
     * @return List of rows as maps (column name -> value)
     */
    List<Map<String, Object>> executeQuery(String query);
    
    /**
     * Get the most recent messages from the configured table
     * 
     * @param limit Maximum number of messages to return
     * @return List of message rows
     */
    List<Map<String, Object>> getRecentMessages(int limit);
    
    /**
     * Get message counts grouped by source
     * 
     * @return List of counts by source
     */
    List<Map<String, Object>> getMessageCountBySource();
    
    /**
     * Get the most recent messages as KafkaMessageRecord objects
     * 
     * @param limit Maximum number of messages to return
     * @return List of KafkaMessageRecord objects
     */
    List<KafkaMessageRecord> getRecentKafkaMessages(int limit);
    
    /**
     * Get messages by HTTP status code
     * 
     * @param statusCode The HTTP status code to filter by
     * @param limit Maximum number of messages to return
     * @return List of KafkaMessageRecord objects
     */
    List<KafkaMessageRecord> getMessagesByStatusCode(int statusCode, int limit);
    
    /**
     * Get messages with errors in the api_response
     * 
     * @param limit Maximum number of messages to return
     * @return List of KafkaMessageRecord objects
     */
    List<KafkaMessageRecord> getMessagesWithErrors(int limit);
    
    /**
     * Insert or update a KafkaMessageRecord
     * 
     * @param record The record to insert or update
     * @return True if successful, false otherwise
     */
    boolean insertOrUpdateRecord(KafkaMessageRecord record);
} 