package com.example.kafkabqperformance.model;

public class TestRequest {
    private int messageCount;
    private int batchSize;

    public TestRequest() {
    }

    public TestRequest(int messageCount, int batchSize) {
        this.messageCount = messageCount;
        this.batchSize = batchSize;
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

    @Override
    public String toString() {
        return "TestRequest{" +
                "messageCount=" + messageCount +
                ", batchSize=" + batchSize +
                '}';
    }
} 