package com.example.kafkabqperformance;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

public class BigQueryConnectionTest {

    public static void main(String[] args) {
        try {
            // Path to the credentials file
            String credentialsPath = "./config/gcp-credentials.json";
            
            // Project ID from the configuration
            String projectId = "jovial-engine-458300-n6";
            
            // Dataset name from the configuration
            String datasetName = "kafka_bq_transactions";
            
            System.out.println("Testing BigQuery connection...");
            System.out.println("Project ID: " + projectId);
            System.out.println("Dataset: " + datasetName);
            System.out.println("Credentials path: " + Paths.get(credentialsPath).toAbsolutePath());
            
            // Load credentials
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream(credentialsPath));
            
            // Initialize BigQuery client
            BigQuery bigQuery = BigQueryOptions.newBuilder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build()
                    .getService();
            
            // Check if the dataset exists
            Dataset dataset = bigQuery.getDataset(DatasetId.of(projectId, datasetName));
            if (dataset != null) {
                System.out.println("Successfully connected to dataset: " + datasetName);
                
                // List tables in the dataset
                System.out.println("\nTables in the dataset:");
                dataset.list().iterateAll().forEach(table -> 
                    System.out.println(" - " + table.getTableId().getTable()));
                
                // Run a simple query
                String query = "SELECT COUNT(*) as count FROM `" + projectId + "." + datasetName + ".kafka_messages`";
                QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
                
                System.out.println("\nRunning query: " + query);
                TableResult result = bigQuery.query(queryConfig);
                
                // Print query results
                result.iterateAll().forEach(row -> 
                    System.out.println("Total records: " + row.get("count").getLongValue()));
                
                System.out.println("\nBigQuery connection test completed successfully!");
            } else {
                System.err.println("Dataset not found: " + datasetName);
            }
        } catch (IOException e) {
            System.err.println("Error loading credentials: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error connecting to BigQuery: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 