package com.example.kafkabqperformance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KafkaBqPerformanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaBqPerformanceApplication.class, args);
    }

}
