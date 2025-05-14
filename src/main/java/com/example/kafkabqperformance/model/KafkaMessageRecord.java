package com.example.kafkabqperformance.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Data model representing the kafka_messages BigQuery table schema
 */
@Data
public class KafkaMessageRecord {
    
    private String uuid;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant receivedTimestamp;
    
    private String rawPayload;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant processingTimestamp;
    
    private Integer httpStatusCode;
    
    private ApiResponse apiResponse;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant submittedDate;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private Instant processDate;
    
    private Integer aspnId;
    
    private Integer rxDataId;
    
    @Data
    public static class ApiResponse {
        private Integer rxDataId;
        private List<String> errors;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        private Instant submittedDate;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        private Instant processDate;
        
        private Integer aspnId;
    }
} 