#!/usr/bin/env pwsh
#
# Simple BigQuery Write API Test
#

# Configuration
$baseUrl = "http://localhost:8081"

Write-Host "BigQuery Write API Simple Test"
Write-Host "----------------------------"

# Test Legacy API
Write-Host "Testing Legacy API with 3 records..."
$body = @{
    messageCount = 3
    batchSize = 3
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/direct/test-legacy" -Method Post -ContentType "application/json" -Body $body -ErrorAction Stop
    Write-Host "Response Status: $($response.StatusCode)"
    Write-Host "Response Content: $($response.Content)"
} catch {
    Write-Host "Error testing Legacy API: $_" -ForegroundColor Red
}

Write-Host ""

# Test Storage Write API
Write-Host "Testing Storage Write API with 3 records..."
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/direct/test-write-api" -Method Post -ContentType "application/json" -Body $body -ErrorAction Stop
    Write-Host "Response Status: $($response.StatusCode)"
    Write-Host "Response Content: $($response.Content)"
} catch {
    Write-Host "Error testing Storage Write API: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "Test complete. Now check BigQuery for new records:"
Write-Host "bq query --use_legacy_sql=false 'SELECT * FROM ``jovial-engine-458300-n6.kafka_bq_transactions.kafka_messages`` ORDER BY received_timestamp DESC LIMIT 10'" 