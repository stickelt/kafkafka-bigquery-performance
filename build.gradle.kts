plugins {
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
    application
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

// Set main class explicitly
springBoot {
    mainClass.set("com.example.kafkabqperformance.KafkaBqPerformanceApplication")
}

// Also set for application plugin
application {
    mainClass.set("com.example.kafkabqperformance.KafkaBqPerformanceApplication")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://packages.confluent.io/maven/") }
    maven { url = uri("https://jitpack.io") }
}

// Force Spring to use these versions for all modules
extra["springCloudGcpVersion"] = "4.8.3"
extra["springCloudVersion"] = "2023.0.0"

dependencyManagement {
    imports {
        mavenBom("com.google.cloud:spring-cloud-gcp-dependencies:${property("springCloudGcpVersion")}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
        mavenBom("com.google.cloud:libraries-bom:26.34.0")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Google BigQuery - using SPECIFIC versions known to work together
    implementation("com.google.cloud:google-cloud-bigquery:2.24.4")
    implementation("com.google.cloud:google-cloud-bigquerystorage:2.24.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.16.0")
    
    // JSON support
    implementation("org.json:json:20230227")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Common Java libraries often needed
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Handle Jar configurations
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    requiresUnpack("**/google-cloud-*.jar")
}

// Explicitly enable resource filtering
tasks.withType<ProcessResources> {
    filesMatching("application*.properties") {
        expand(project.properties)
    }
    filesMatching("application*.yml") {
        expand(project.properties)
    }
}

// Ensure proper handling of dependency conflicts
configurations.all {
    resolutionStrategy {
        // Force specific versions for problematic dependencies
        force("org.jetbrains.kotlin:kotlin-stdlib:1.6.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:1.6.21")
    }
} 