package com.example.kafkabqperformance.service;

import com.example.kafkabqperformance.model.KafkaMessage;

import java.util.List;

public interface BigQueryWriteService {
    
    /**
     * Writes a batch of Kafka messages to BigQuery
     * 
     * @param messages List of Kafka messages to write
     * @return Number of successfully written records
     */
    int writeToBigQuery(List<KafkaMessage> messages);
    
    /**
     * Flushes any pending writes to BigQuery
     * 
     * @return Number of successfully flushed records
     */
    int flush();
}
