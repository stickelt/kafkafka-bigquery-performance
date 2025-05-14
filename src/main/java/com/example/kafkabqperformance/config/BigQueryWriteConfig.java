package com.example.kafkabqperformance.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.storage.v1.BigQueryWriteClient;
import com.google.cloud.bigquery.storage.v1.BigQueryWriteSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class BigQueryWriteConfig {

    @Bean
    public BigQueryWriteClient bigQueryWriteClient() throws IOException {
        // Use application default credentials to simplify setup
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        
        BigQueryWriteSettings settings = BigQueryWriteSettings.newBuilder()
            .setCredentialsProvider(() -> credentials)
            .build();
            
        return BigQueryWriteClient.create(settings);
    }
} 