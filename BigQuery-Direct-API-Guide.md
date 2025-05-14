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

## PowerShell Commands

### Starting the Application
```powershell
cd 2025Stuff\kafkafka-bigquery-performance
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
# View latest 10 records
bq query --use_legacy_sql=false 'SELECT * FROM `jovial-engine-458300-n6.kafka_bq_transactions.kafka_messages` LIMIT 10'

# Count records
bq query --use_legacy_sql=false 'SELECT COUNT(*) as record_count FROM `jovial-engine-458300-n6.kafka_bq_transactions.kafka_messages`'
```

### Inserting Records Directly to BigQuery
```powershell
# Using bq command
echo '{"id":"powershell-123","message":"Test from PowerShell","timestamp":"2025-05-13T20:00:00Z","source":"powershell","priority":1}' > temp_record.json
bq load --source_format=NEWLINE_DELIMITED_JSON jovial-engine-458300-n6:kafka_bq_transactions.kafka_messages temp_record.json id:string,message:string,timestamp:timestamp,source:string,priority:integer
Remove-Item temp_record.json

# Using Google Cloud REST API
$env:GOOGLE_APPLICATION_CREDENTIALS="./config/gcp-credentials.json"
$projectId = "jovial-engine-458300-n6"
$datasetId = "kafka_bq_transactions"
$tableId = "kafka_messages"

$data = @{
  "kind" = "bigquery#tableDataInsertAllRequest"
  "rows" = @(
    @{
      "json" = @{
        "id" = "ps-$(Get-Random)"
        "message" = "Direct insert from PowerShell"
        "timestamp" = (Get-Date).ToString("o")
        "source" = "powershell-direct"
        "priority" = 1
      }
    }
  )
}

$body = $data | ConvertTo-Json -Depth 10
Invoke-RestMethod -Method Post -Uri "https://bigquery.googleapis.com/bigquery/v2/projects/$projectId/datasets/$datasetId/tables/$tableId/insertAll" -Body $body -ContentType "application/json" -Headers @{Authorization = "Bearer $(gcloud auth print-access-token)"}
``` 