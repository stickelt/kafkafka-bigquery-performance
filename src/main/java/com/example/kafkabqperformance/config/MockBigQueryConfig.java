package com.example.kafkabqperformance.config;

import com.example.kafkabqperformance.service.BigQueryWriteService;
import com.example.kafkabqperformance.service.MockBigQueryWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for running without BigQuery
 * This class provides mock implementations of BigQuery services
 */
@Configuration
@Profile("nobq")
public class MockBigQueryConfig {

    private static final Logger logger = LoggerFactory.getLogger(MockBigQueryConfig.class);

    @Bean(name = "legacyBigQueryWriteService")
    @Primary
    public BigQueryWriteService legacyBigQueryWriteService() {
        return new MockBigQueryWriteService();
    }

    @Bean(name = "writeApiBigQueryWriteService")
    @Primary
    public BigQueryWriteService writeApiBigQueryWriteService() {
        return new MockBigQueryWriteService();
    }
}
