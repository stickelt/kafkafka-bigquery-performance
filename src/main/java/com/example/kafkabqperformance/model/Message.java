package com.example.kafkabqperformance.model;

import java.time.Instant;

public class Message {
    private String id;
    private String content;
    private Instant timestamp;
    private String topic;
    private int partition;

    public Message() {
    }

    public Message(String id, String content, Instant timestamp, String topic, int partition) {
        this.id = id;
        this.content = content;
        this.timestamp = timestamp;
        this.topic = topic;
        this.partition = partition;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getPartition() {
        return partition;
    }

    public void setPartition(int partition) {
        this.partition = partition;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", topic='" + topic + '\'' +
                ", partition=" + partition +
                '}';
    }
} 