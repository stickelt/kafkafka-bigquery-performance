package com.example.kafkabqperformance.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaMessage {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("priority")
    private Integer priority;
    
    // Add any other fields that might be in your Kafka messages
}
