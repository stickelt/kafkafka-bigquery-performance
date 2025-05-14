# Kafka to BigQuery Performance Testing Tool

This application tests the performance of writing messages from Kafka to BigQuery using different APIs.

## Build System

This project uses Gradle with Kotlin DSL for build configuration.

### Gradle Wrapper Commands

- Build the project:
  ```bash
  ./gradlew build
  ```

- Run the application:
  ```bash
  ./gradlew bootRun
  ```

- Run with a specific profile:
  ```bash
  ./gradlew bootRun --args='--spring.profiles.active=local'
  ```

- Execute tests:
  ```bash
  ./gradlew test
  ```

## Environment Configuration

This application uses Spring profiles to manage environment-specific configurations.

### Available Profiles:

- **local**: For local development and testing
- **dev**: For development environment
- **Add more profiles as needed**: qa, staging, prod, etc.

### Running with Different Profiles:

1. **Local Development**:
   ```
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. **Development Environment**:
   ```
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. **Multiple Profiles** (applying both local and a custom profile):
   ```
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,custom
   ```

### Environment Variables:

Each profile can use environment variables to override settings:

- For BigQuery:
  - `BQ_PROJECT_ID`: Google Cloud project ID
  - `BQ_DATASET`: BigQuery dataset name
  - `BQ_TABLE`: BigQuery table name
  - `BQ_CREDENTIALS_PATH`: Path to the service account credentials file

- For performance testing:
  - `BATCH_SIZE`: Number of messages per batch
  - `FLUSH_INTERVAL_MS`: Flush interval in milliseconds

## Adding New Environments:

To add a new environment:

1. Create a new profile file: `application-{env}.yaml` (replace {env} with environment name)
2. Configure the necessary settings for that environment
3. Run the application with the corresponding profile

Example for adding a production environment:
1. Create `application-prod.yaml`
2. Run with: `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`

## API Endpoints

- `GET /api/health`: Check if the service is running
- `POST /api/test-legacy`: Test the legacy BigQuery write method
- `POST /api/test-write-api`: Test the Storage Write API method

Example payload:
```json
{
  "messageCount": 1000,
  "batchSize": 100
}
```

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
