#!/usr/bin/env pwsh
#
# BigQuery Write API Benchmark Script
# Tests both Legacy and Storage Write API performance
#

# Configuration
$baseUrl = "http://localhost:8081"
$iterations = 5  # Run multiple iterations to get better average
$recordCounts = @(10, 100, 1000)  # Test with different record counts
$batchSizes = @(1, 10, 50)  # Test with different batch sizes

# Results arrays
$results = @()

Write-Host "BigQuery Write API Performance Benchmark"
Write-Host "--------------------------------------"
Write-Host ""

# Function to run a single test
function Run-Test {
    param (
        [string]$apiType,
        [int]$recordCount,
        [int]$batchSize
    )
    
    $endpoint = if ($apiType -eq "Legacy") { "$baseUrl/api/direct/test-legacy" } else { "$baseUrl/api/direct/test-write-api" }
    $body = @{
        messageCount = $recordCount
        batchSize = $batchSize
    } | ConvertTo-Json
    
    try {
        $response = Invoke-RestMethod -Uri $endpoint -Method Post -ContentType "application/json" -Body $body
        return $response.durationMs
    }
    catch {
        Write-Error "Failed to call $endpoint - $_"
        return -1
    }
}

# Run benchmarks
foreach ($recordCount in $recordCounts) {
    foreach ($batchSize in $batchSizes) {
        if ($batchSize -gt $recordCount) {
            continue  # Skip if batch size is larger than record count
        }
        
        Write-Host "Testing with $recordCount records, batch size $batchSize"
        
        # Test Legacy API
        Write-Host "  Legacy API: " -NoNewline
        $legacyTimes = @()
        for ($i = 0; $i -lt $iterations; $i++) {
            $duration = Run-Test -apiType "Legacy" -recordCount $recordCount -batchSize $batchSize
            if ($duration -ge 0) {
                $legacyTimes += $duration
                Write-Host "$duration ms" -NoNewline
                if ($i -lt $iterations - 1) {
                    Write-Host ", " -NoNewline
                }
            }
        }
        Write-Host ""
        
        # Test Write API
        Write-Host "  Storage Write API: " -NoNewline
        $writeApiTimes = @()
        for ($i = 0; $i -lt $iterations; $i++) {
            $duration = Run-Test -apiType "WriteApi" -recordCount $recordCount -batchSize $batchSize
            if ($duration -ge 0) {
                $writeApiTimes += $duration
                Write-Host "$duration ms" -NoNewline
                if ($i -lt $iterations - 1) {
                    Write-Host ", " -NoNewline
                }
            }
        }
        Write-Host ""
        
        # Calculate averages
        $legacyAvg = if ($legacyTimes.Count -gt 0) { ($legacyTimes | Measure-Object -Average).Average } else { 0 }
        $writeApiAvg = if ($writeApiTimes.Count -gt 0) { ($writeApiTimes | Measure-Object -Average).Average } else { 0 }
        
        # Store results
        $results += [PSCustomObject]@{
            RecordCount = $recordCount
            BatchSize = $batchSize
            LegacyAvgMs = [math]::Round($legacyAvg, 2)
            WriteApiAvgMs = [math]::Round($writeApiAvg, 2)
            DifferencePercent = if ($legacyAvg -gt 0) { [math]::Round(($writeApiAvg - $legacyAvg) / $legacyAvg * 100, 2) } else { "N/A" }
            Faster = if ($legacyAvg -eq 0 -or $writeApiAvg -eq 0) { "Unknown" } elseif ($legacyAvg -lt $writeApiAvg) { "Legacy" } else { "WriteApi" }
        }
        
        Write-Host ""
    }
}

# Display results as a table
Write-Host ""
Write-Host "Benchmark Results"
Write-Host "----------------"

$results | Format-Table -Property RecordCount, BatchSize, LegacyAvgMs, WriteApiAvgMs, DifferencePercent, Faster

# Save results to CSV
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$csvPath = "benchmark_results_$timestamp.csv"
$results | Export-Csv -Path $csvPath -NoTypeInformation

Write-Host "Results saved to: $csvPath" 