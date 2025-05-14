package com.example.kafkabqperformance;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;

@SpringBootApplication
public class BigQueryConnectionTester {

    public static void main(String[] args) {
        SpringApplication.run(BigQueryConnectionTester.class, "--spring.profiles.active=local");
    }

    @Bean
    @Profile("local")
    public CommandLineRunner testBigQuery(BigQuery bigQuery) {
        return args -> {
            try {
                System.out.println("Testing BigQuery connection...");
                
                // Run a simple query to test the connection
                String query = "SELECT 'Connection successful!' as message, CURRENT_TIMESTAMP() as timestamp";
                QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
                
                System.out.println("Executing query: " + query);
                TableResult result = bigQuery.query(queryConfig);
                
                // Print the query result
                result.iterateAll().forEach(row -> {
                    String message = row.get("message").getStringValue();
                    String timestamp = row.get("timestamp").getStringValue();
                    System.out.println(message + " at " + timestamp);
                });
                
                // Try to query the kafka_messages table
                System.out.println("\nTrying to query the kafka_messages table...");
                String tableQuery = "SELECT COUNT(*) as count FROM `kafka_bq_transactions.kafka_messages`";
                QueryJobConfiguration tableQueryConfig = QueryJobConfiguration.newBuilder(tableQuery).build();
                
                TableResult tableResult = bigQuery.query(tableQueryConfig);
                tableResult.iterateAll().forEach(row -> {
                    long count = row.get("count").getLongValue();
                    System.out.println("Found " + count + " records in kafka_messages table");
                });
                
                System.out.println("\nBigQuery connection test completed successfully!");
            } catch (Exception e) {
                System.err.println("Error connecting to BigQuery: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
} 