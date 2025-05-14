# Check if the service is running
Write-Host "Testing health endpoint..."
Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get

# Generate and send test data to the write API endpoint
Write-Host "`nTesting write API endpoint with 10 messages..."
$body = @{
    messageCount = 10
    batchSize = 5
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/test-write-api" -Method Post -Body $body -ContentType "application/json"
} catch {
    Write-Host "Error testing write API: $_"
    Write-Host $_.Exception.Response
}

# Generate and send test data to the legacy endpoint
Write-Host "`nTesting legacy endpoint with 10 messages..."
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/test-legacy" -Method Post -Body $body -ContentType "application/json"
} catch {
    Write-Host "Error testing legacy API: $_"
    Write-Host $_.Exception.Response
} 