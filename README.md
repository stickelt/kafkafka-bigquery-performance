# Kafka to BigQuery Performance Comparison

This Spring Boot application demonstrates and compares two different approaches for streaming Kafka messages to BigQuery:

1. **Legacy WriteAll API** - Using the traditional BigQuery client library with `insertAll` method
2. **Storage Write API** - Using the newer BigQuery Storage Write API for improved performance

## Deployment Options

This application can be deployed as a standalone Spring Boot application or containerized and deployed to Kubernetes environments like Google Kubernetes Engine (GKE).

## Features

- Consumes messages from a Kafka topic
- Processes messages in configurable batch sizes
- Writes messages to BigQuery using two different approaches in parallel
- Tracks and reports performance metrics for comparison
- Provides REST endpoints for testing and monitoring

## Configuration

The application is configured via `application.yaml`. Key configuration properties include:

```yaml
# BigQuery Configuration
bigquery:
  project-id: ${BQ_PROJECT_ID:your-gcp-project-id}
  dataset: ${BQ_DATASET:your_dataset}
  table: ${BQ_TABLE:your_table}
  credentials-path: ${BQ_CREDENTIALS_PATH:/path/to/your/credentials.json}

# Kafka Topics Configuration
kafka:
  topic: ${KAFKA_TOPIC:test-topic}
  
# Performance Configuration
performance:
  batch-size: ${BATCH_SIZE:1000}
  flush-interval-ms: ${FLUSH_INTERVAL_MS:5000}
```

These properties can be overridden via environment variables or command-line arguments.

## Prerequisites

- Java 17 or higher
- Gradle
- Access to a Kafka cluster
- Access to a BigQuery instance with appropriate permissions
- GCP service account credentials with BigQuery access

## Building the Application

```bash
./gradlew build
```

## Running the Application

```bash
./gradlew bootRun
```

Or with custom configuration:

```bash
java -jar build/libs/kafka-bq-performance-0.0.1-SNAPSHOT.jar \
  --bigquery.project-id=your-project \
  --bigquery.dataset=your_dataset \
  --bigquery.table=your_table \
  --bigquery.credentials-path=/path/to/credentials.json \
  --kafka.topic=your-topic
```

## API Endpoints

### Health Check

```
GET /api/health
```

### Test Legacy WriteAll API

```
POST /api/test-legacy

{
  "messageCount": 10000,
  "batchSize": 500
}
```

### Test Storage Write API

```
POST /api/test-write-api

{
  "messageCount": 10000,
  "batchSize": 500
}
```

## Performance Monitoring

The application automatically logs performance metrics every minute, comparing the throughput and latency of both BigQuery write approaches. These metrics include:

- Total records processed
- Total processing time
- Minimum and maximum batch processing times
- Average time per record
- Throughput (records per second)

## BigQuery Table Schema

The application expects a BigQuery table with the following schema:

```
id: STRING
message: STRING
timestamp: TIMESTAMP
source: STRING
priority: INTEGER
insert_time: TIMESTAMP
```

## Troubleshooting

- Ensure your GCP credentials have the necessary permissions for BigQuery operations
- Verify Kafka connectivity and topic existence
- Check application logs for detailed error messages
- Adjust batch sizes and flush intervals based on your specific workload characteristics

## Monitoring and Alerting in GKE

When deploying this application to Google Kubernetes Engine (GKE), several monitoring and alerting options are available:

### GCP-Native Solutions

#### Cloud Monitoring (formerly Stackdriver)
- Automatic metrics collection for GKE clusters, nodes, and containers
- Custom metrics API to publish application-specific metrics
- Alerting policies based on metric thresholds
- Integration with Cloud Logging for log-based alerts

#### Cloud Logging
- Centralized log management for all GKE components
- Log-based metrics to create custom metrics from log entries
- Log-based alerts for error patterns or specific log messages

#### Cloud Trace and Profiler
- Distributed tracing for request latency analysis
- CPU and heap profiling for production applications

### Implementation Options

#### 1. Micrometer with Google Cloud Monitoring

Add the following dependencies to your `build.gradle`:

```gradle
implementation 'io.micrometer:micrometer-registry-stackdriver:latest.release'
implementation 'io.micrometer:micrometer-core:latest.release'
```

Create a configuration class:

```java
@Configuration
public class MetricsConfig {
    @Bean
    public MeterRegistry meterRegistry() {
        return StackdriverMeterRegistry.builder(new StackdriverConfig() {
            @Override
            public String get(String key) {
                return null;
            }
            @Override
            public String projectId() {
                return "your-gcp-project-id";
            }
        }).build();
    }
}
```

#### 2. Spring Boot Actuator with Prometheus

Add the following dependencies:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

Configure in `application.yaml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

Deploy Prometheus and Grafana to your GKE cluster using Helm:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install prometheus prometheus-community/kube-prometheus-stack
```

#### 3. Kubernetes Health Probes

In your Kubernetes deployment manifest:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 30
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
```

#### 4. Custom Metrics Adapter

Deploy the Stackdriver Custom Metrics Adapter to scale based on your application metrics:

```bash
helm install stackdriver-adapter stackdriver-adapter/stackdriver-adapter \
  --set stackdriver.projectId=your-gcp-project-id
```

Create a HorizontalPodAutoscaler based on your custom metrics:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: kafka-bq-performance
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: kafka-bq-performance
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: External
    external:
      metric:
        name: custom.googleapis.com/kafka_lag
      target:
        type: AverageValue
        averageValue: 100
```
