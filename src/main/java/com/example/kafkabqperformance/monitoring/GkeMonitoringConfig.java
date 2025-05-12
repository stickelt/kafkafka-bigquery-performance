package com.example.kafkabqperformance.monitoring;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for monitoring when running in Google Kubernetes Engine (GKE).
 * 
 * <p>This class is activated only when the "gke" profile is active.</p>
 * 
 * <p>To use this configuration:</p>
 * <ol>
 *   <li>Add the following dependencies to build.gradle:
 *     <pre>
 *     implementation 'org.springframework.boot:spring-boot-starter-actuator'
 *     implementation 'io.micrometer:micrometer-registry-stackdriver:latest.release'
 *     implementation 'io.micrometer:micrometer-core:latest.release'
 *     </pre>
 *   </li>
 *   <li>Start the application with the "gke" profile:
 *     <pre>
 *     java -jar app.jar --spring.profiles.active=gke
 *     </pre>
 *   </li>
 *   <li>Or in Kubernetes deployment:
 *     <pre>
 *     env:
 *     - name: SPRING_PROFILES_ACTIVE
 *       value: gke
 *     </pre>
 *   </li>
 * </ol>
 */
@Configuration
@Profile("gke")
public class GkeMonitoringConfig {

    @Value("${spring.application.name:kafka-bq-performance}")
    private String applicationName;
    
    @Value("${gcp.project-id:}")
    private String projectId;
    
    /**
     * Customizes the MeterRegistry with common tags for all metrics.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(Arrays.asList(
                        Tag.of("application", applicationName),
                        Tag.of("environment", "gke")
                ));
    }
    
    /**
     * Registers JVM metrics to monitor memory usage, garbage collection, and CPU usage.
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics(MeterRegistry registry) {
        JvmMemoryMetrics memoryMetrics = new JvmMemoryMetrics();
        memoryMetrics.bindTo(registry);
        return memoryMetrics;
    }
    
    @Bean
    public JvmGcMetrics jvmGcMetrics(MeterRegistry registry) {
        JvmGcMetrics gcMetrics = new JvmGcMetrics();
        gcMetrics.bindTo(registry);
        return gcMetrics;
    }
    
    @Bean
    public ProcessorMetrics processorMetrics(MeterRegistry registry) {
        ProcessorMetrics processorMetrics = new ProcessorMetrics();
        processorMetrics.bindTo(registry);
        return processorMetrics;
    }
    
    /**
     * Example of how to use the MeterRegistry to track BigQuery write performance.
     * This bean demonstrates the usage pattern but is not automatically wired to the services.
     */
    @Bean
    public BigQueryMetrics bigQueryMetrics(MeterRegistry registry) {
        return new BigQueryMetrics(registry);
    }
    
    /**
     * Helper class to track BigQuery write metrics.
     */
    public static class BigQueryMetrics {
        private final Timer legacyWriteTimer;
        private final Timer writeApiTimer;
        private final MeterRegistry registry;
        
        public BigQueryMetrics(MeterRegistry registry) {
            this.registry = registry;
            this.legacyWriteTimer = Timer.builder("bigquery.write.legacy")
                    .description("Timer for Legacy BigQuery WriteAll API")
                    .register(registry);
            this.writeApiTimer = Timer.builder("bigquery.write.storage_api")
                    .description("Timer for BigQuery Storage Write API")
                    .register(registry);
            
            // Register gauges and counters
            registry.gauge("bigquery.pending_records.legacy", 0);
            registry.gauge("bigquery.pending_records.storage_api", 0);
            registry.counter("bigquery.records.total.legacy");
            registry.counter("bigquery.records.total.storage_api");
            registry.counter("bigquery.errors.legacy");
            registry.counter("bigquery.errors.storage_api");
        }
        
        /**
         * Records the time taken for a legacy BigQuery write operation.
         *
         * @param durationMs Duration in milliseconds
         * @param recordCount Number of records processed
         */
        public void recordLegacyWriteTime(long durationMs, int recordCount) {
            legacyWriteTimer.record(durationMs, TimeUnit.MILLISECONDS);
            registry.counter("bigquery.records.total.legacy").increment(recordCount);
        }
        
        /**
         * Records the time taken for a Storage Write API operation.
         *
         * @param durationMs Duration in milliseconds
         * @param recordCount Number of records processed
         */
        public void recordWriteApiTime(long durationMs, int recordCount) {
            writeApiTimer.record(durationMs, TimeUnit.MILLISECONDS);
            registry.counter("bigquery.records.total.storage_api").increment(recordCount);
        }
        
        /**
         * Updates the gauge for pending records.
         *
         * @param isLegacy Whether this is for the legacy API
         * @param count Current count of pending records
         */
        public void updatePendingRecords(boolean isLegacy, int count) {
            String metricName = isLegacy ? "bigquery.pending_records.legacy" : "bigquery.pending_records.storage_api";
            registry.gauge(metricName, count);
        }
        
        /**
         * Records an error in BigQuery operations.
         *
         * @param isLegacy Whether this is for the legacy API
         */
        public void recordError(boolean isLegacy) {
            String metricName = isLegacy ? "bigquery.errors.legacy" : "bigquery.errors.storage_api";
            registry.counter(metricName).increment();
        }
    }
}
