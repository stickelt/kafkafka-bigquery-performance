# BigQuery Direct API Guide

## Overview
This document explains how to interact with the Direct BigQuery API service without requiring a Kafka broker.

## API Configuration
- Server port: **8081**
- API Base URL: `http://localhost:8081`

## Endpoints

### Health Check
```
GET /api/health
```

### Test Legacy BigQuery API
```
POST /api/direct/test-legacy
```
Request body:
```json
{
  "messageCount": 100,
  "batchSize": 20
}
```

### Test Write API (Storage Write API)
```
POST /api/direct/test-write-api
```
Request body:
```json
{
  "messageCount": 100,
  "batchSize": 20
}
```

## Important Implementation Note
The application uses a buffered write approach. Records are first queued in memory and then written in batches by calling `flush()`. The controller has been updated to always call `flush()` after processing messages, ensuring data is committed to BigQuery.

## Testing Scripts

### Quick Test Script (testbq.ps1)
A simple script that tests both APIs with a small number of records:
```powershell
.\testbq.ps1
```

### Benchmark Script (benchmark.ps1)
A comprehensive benchmark that compares both APIs with different record counts and batch sizes:
```powershell
.\benchmark.ps1
```
The benchmark generates a CSV file with performance metrics.

## PowerShell Commands

### Starting the Application
```powershell
cd C:\dev\2025Stuff\kafkafka-bigquery-performance
.\gradlew.bat bootRun --args='--spring.profiles.active=nokafka'
```

### Calling the APIs
```powershell
# Health Check
Invoke-WebRequest -Uri "http://localhost:8081/api/health" -Method Get

# Test Legacy API
$body = @{messageCount=100; batchSize=20} | ConvertTo-Json
Invoke-WebRequest -Uri "http://localhost:8081/api/direct/test-legacy" -Method Post -ContentType "application/json" -Body $body

# Test Write API
$body = @{messageCount=100; batchSize=20} | ConvertTo-Json
Invoke-WebRequest -Uri "http://localhost:8081/api/direct/test-write-api" -Method Post -ContentType "application/json" -Body $body
```

### Querying BigQuery from PowerShell
```powershell
# Show table schema
bq show --schema jovial-engine-458300-n6:kafka_bq_transactions.kafka_messages

# View latest records
bq query --use_legacy_sql=false 'SELECT * FROM `jovial-engine-458300-n6.kafka_bq_transactions.kafka_messages` ORDER BY received_timestamp DESC LIMIT 10'

# Count records
bq query --use_legacy_sql=false 'SELECT COUNT(*) as record_count FROM `jovial-engine-458300-n6.kafka_bq_transactions.kafka_messages`'

# Find PowerShell-inserted records
bq query --use_legacy_sql=false 'SELECT * FROM `jovial-engine-458300-n6.kafka_bq_transactions.kafka_messages` WHERE uuid LIKE "ps-%" ORDER BY received_timestamp DESC LIMIT 5'
```

### Inserting Records Directly to BigQuery
```powershell
# Set credentials first
$env:GOOGLE_APPLICATION_CREDENTIALS="./config/gcp-credentials.json"

# Insert a single record using REST API
$projectId = "jovial-engine-458300-n6"
$datasetId = "kafka_bq_transactions"
$tableId = "kafka_messages"

$data = @{
  "rows" = @(
    @{
      "json" = @{
        "uuid" = "ps-" + (Get-Random)
        "received_timestamp" = (Get-Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")
        "raw_payload" = "Test payload from PowerShell"
        "processing_timestamp" = (Get-Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")
        "http_status_code" = 200
        "api_response" = @{
          "rx_data_id" = 12345
          "errors" = @()
          "submitted_date" = (Get-Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")
          "process_date" = (Get-Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")
          "aspn_id" = 67890
        }
        "submitted_date" = (Get-Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")
        "process_date" = (Get-Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")
        "aspn_id" = 67890
        "rx_data_id" = 12345
      }
    }
  )
}

$body = $data | ConvertTo-Json -Depth 10
$token = & gcloud auth print-access-token
Invoke-RestMethod -Method Post -Uri "https://bigquery.googleapis.com/bigquery/v2/projects/$projectId/datasets/$datasetId/tables/$tableId/insertAll" -Body $body -ContentType "application/json" -Headers @{Authorization = "Bearer $token"}
```

## Troubleshooting
- **No records appearing in BigQuery**: Check if the `flush()` method is being called. Without this, records remain in memory but aren't committed to BigQuery.
- **Authentication errors**: Verify that the gcp-credentials.json file is correctly placed in the config directory.
- **Connection errors**: Ensure the application is running and accessible at the correct port before running any test scripts. 