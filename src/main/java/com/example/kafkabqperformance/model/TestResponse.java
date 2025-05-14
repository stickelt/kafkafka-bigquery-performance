package com.example.kafkabqperformance.model;

public class TestResponse {
    private int messageCount;
    private int batchSize;
    private long durationMs;
    private String message;

    public TestResponse() {
    }

    public TestResponse(int messageCount, int batchSize, long durationMs, String message) {
        this.messageCount = messageCount;
        this.batchSize = batchSize;
        this.durationMs = durationMs;
        this.message = message;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "TestResponse{" +
                "messageCount=" + messageCount +
                ", batchSize=" + batchSize +
                ", durationMs=" + durationMs +
                ", message='" + message + '\'' +
                '}';
    }
} 