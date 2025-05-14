package com.example.kafkabqperformance;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.bigquery.QueryJobConfiguration;

/**
 * Simple standalone class to test BigQuery connectivity
 * This helps isolate whether the issue is with Spring Boot or with the BigQuery dependencies
 */
public class SimpleBigQueryTest {

    public static void main(String[] args) {
        try {
            // Get default credentials - will use GOOGLE_APPLICATION_CREDENTIALS if set
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            System.out.println("Successfully loaded credentials");
            
            // Initialize BigQuery with those credentials
            BigQuery bigquery = BigQueryOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();
            
            System.out.println("Successfully created BigQuery client");
            
            // Run a simple query
            String query = "SELECT 1 as test";
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            
            System.out.println("Executing query: " + query);
            TableResult results = bigquery.query(queryConfig);
            
            System.out.println("Query executed successfully. Results:");
            results.iterateAll().forEach(row -> System.out.println(row.get("test").getStringValue()));
            
            System.out.println("Test completed successfully!");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 