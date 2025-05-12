package com.example.kafkabqperformance.monitoring;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring component that tracks and reports metrics for both BigQuery write approaches.
 * 
 * <p>When running in Google Kubernetes Engine (GKE), this component can be enhanced with:</p>
 * <ul>
 *   <li>Micrometer integration to expose metrics to Cloud Monitoring (Stackdriver)</li>
 *   <li>Prometheus integration for metrics collection and visualization in Grafana</li>
 *   <li>OpenTelemetry for distributed tracing and metrics</li>
 * </ul>
 * 
 * <p>GKE Monitoring Options:</p>
 * <ul>
 *   <li>Cloud Monitoring - For metrics collection, dashboards, and alerting</li>
 *   <li>Cloud Logging - For log aggregation and log-based metrics</li>
 *   <li>Cloud Trace - For distributed tracing and latency analysis</li>
 *   <li>Cloud Profiler - For CPU and heap profiling</li>
 * </ul>
 * 
 * <p>For implementation details, see the README.md file.</p>
 */
@Component
@Slf4j
public class PerformanceMonitor {

    private final AtomicLong legacyTotalTime = new AtomicLong(0);
    private final AtomicLong legacyTotalRecords = new AtomicLong(0);
    private final AtomicLong legacyMaxTime = new AtomicLong(0);
    private final AtomicLong legacyMinTime = new AtomicLong(Long.MAX_VALUE);
    
    private final AtomicLong writeApiTotalTime = new AtomicLong(0);
    private final AtomicLong writeApiTotalRecords = new AtomicLong(0);
    private final AtomicLong writeApiMaxTime = new AtomicLong(0);
    private final AtomicLong writeApiMinTime = new AtomicLong(Long.MAX_VALUE);
    
    private Instant startTime = Instant.now();
    
    /**
     * Record performance metrics for a batch operation
     * 
     * @param isLegacy true if using legacy WriteAll API, false if using Storage Write API
     * @param recordCount number of records processed
     * @param durationMs time taken in milliseconds
     */
    public void recordBatchPerformance(boolean isLegacy, int recordCount, long durationMs) {
        if (isLegacy) {
            legacyTotalTime.addAndGet(durationMs);
            legacyTotalRecords.addAndGet(recordCount);
            updateMinMax(legacyMinTime, legacyMaxTime, durationMs);
        } else {
            writeApiTotalTime.addAndGet(durationMs);
            writeApiTotalRecords.addAndGet(recordCount);
            updateMinMax(writeApiMinTime, writeApiMaxTime, durationMs);
        }
    }
    
    private void updateMinMax(AtomicLong min, AtomicLong max, long value) {
        // Update max if new value is larger
        long currentMax = max.get();
        while (value > currentMax) {
            if (max.compareAndSet(currentMax, value)) {
                break;
            }
            currentMax = max.get();
        }
        
        // Update min if new value is smaller
        long currentMin = min.get();
        while (value < currentMin) {
            if (min.compareAndSet(currentMin, value)) {
                break;
            }
            currentMin = min.get();
        }
    }
    
    /**
     * Reports performance metrics at a scheduled interval.
     * 
     * <p>When running in GKE, this method could be enhanced to:</p>
     * <ul>
     *   <li>Publish metrics to Cloud Monitoring using the Monitoring API</li>
     *   <li>Expose metrics via Micrometer for Prometheus scraping</li>
     *   <li>Send alerts based on performance thresholds</li>
     *   <li>Store historical metrics in BigQuery for long-term analysis</li>
     * </ul>
     * 
     * <p>Example integration with Cloud Monitoring:</p>
     * <pre>
     * // Add the following dependencies to build.gradle
     * // implementation 'com.google.cloud:google-cloud-monitoring:2.x.x'
     * 
     * private final MetricServiceClient metricServiceClient;
     * 
     * // In this method:
     * TimeSeriesData.Builder timeSeriesData = TimeSeriesData.newBuilder();
     * timeSeriesData.addPointData(PointData.newBuilder()
     *     .setValue(TypedValue.newBuilder().setDoubleValue(writeApiStats.getThroughput()))
     *     .setTimeInterval(TimeInterval.newBuilder()
     *         .setEndTime(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
     *         .build())
     *     .build());
     * 
     * // Then create and send the time series
     * </pre>
     */
    @Scheduled(fixedRate = 60000) // Report every minute
    public void reportPerformance() {
        PerformanceStats legacyStats = calculateStats(legacyTotalRecords.get(), legacyTotalTime.get(), 
                legacyMinTime.get(), legacyMaxTime.get());
        
        PerformanceStats writeApiStats = calculateStats(writeApiTotalRecords.get(), writeApiTotalTime.get(), 
                writeApiMinTime.get(), writeApiMaxTime.get());
        
        log.info("\n===== PERFORMANCE REPORT =====\n" +
                "Runtime: {} minutes\n" +
                "\nLegacy WriteAll API:\n{}\n" +
                "\nStorage Write API:\n{}\n" +
                "\nPerformance Comparison:\n" +
                "Throughput Ratio (Write API / Legacy): {}x\n" +
                "============================\n",
                (Instant.now().getEpochSecond() - startTime.getEpochSecond()) / 60,
                legacyStats,
                writeApiStats,
                writeApiStats.getThroughput() / Math.max(1, legacyStats.getThroughput()));
        
        // TODO: When running in GKE, consider adding the following:
        // 1. Publish metrics to Cloud Monitoring
        // 2. Check for alert conditions (e.g., throughput below threshold)
        // 3. Export metrics to Prometheus if configured
    }
    
    private PerformanceStats calculateStats(long records, long totalTime, long minTime, long maxTime) {
        PerformanceStats stats = new PerformanceStats();
        stats.setTotalRecords(records);
        stats.setTotalTimeMs(totalTime);
        stats.setMinBatchTimeMs(minTime == Long.MAX_VALUE ? 0 : minTime);
        stats.setMaxBatchTimeMs(maxTime);
        
        if (totalTime > 0) {
            stats.setAvgBatchTimeMs(totalTime / Math.max(1, records));
            stats.setThroughput((double) records / totalTime * 1000); // records per second
        }
        
        return stats;
    }
    
    @Data
    private static class PerformanceStats {
        private long totalRecords;
        private long totalTimeMs;
        private long minBatchTimeMs;
        private long maxBatchTimeMs;
        private long avgBatchTimeMs;
        private double throughput; // records per second
        
        @Override
        public String toString() {
            return String.format("Total Records: %d\n" +
                    "Total Time: %.2f seconds\n" +
                    "Min Batch Time: %d ms\n" +
                    "Max Batch Time: %d ms\n" +
                    "Avg Time Per Record: %.2f ms\n" +
                    "Throughput: %.2f records/second",
                    totalRecords,
                    totalTimeMs / 1000.0,
                    minBatchTimeMs,
                    maxBatchTimeMs,
                    avgBatchTimeMs,
                    throughput);
        }
    }
}
