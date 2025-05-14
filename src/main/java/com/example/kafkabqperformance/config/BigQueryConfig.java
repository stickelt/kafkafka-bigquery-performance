package com.example.kafkabqperformance.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class BigQueryConfig {

    @Value("${bigquery.project-id}")
    private String projectId;

    @Value("${bigquery.credentials-path:/config/gcp-credentials.json}")
    private String credentialsPath;

    @Bean
    public BigQuery bigQuery() throws IOException {
        GoogleCredentials credentials;

        try {
            // Try to load credentials from the specified path
            File credentialsFile = ResourceUtils.getFile(credentialsPath);
            try (FileInputStream serviceAccountStream = new FileInputStream(credentialsFile)) {
                credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
            }
        } catch (Exception e) {
            // Fall back to application default credentials
            System.out.println("Unable to load credentials from file: " + credentialsPath + 
                               ". Falling back to application default credentials. Error: " + e.getMessage());
            credentials = GoogleCredentials.getApplicationDefault();
        }

        return BigQueryOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }
}
