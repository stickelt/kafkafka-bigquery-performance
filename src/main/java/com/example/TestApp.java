package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple test application to verify that Spring Boot is working
 */
@SpringBootApplication
public class TestApp {

    public static void main(String[] args) {
        SpringApplication.run(TestApp.class, args);
    }
    
    @RestController
    public static class TestController {
        
        @GetMapping("/")
        public Map<String, Object> home() {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "UP");
            response.put("message", "Test application is running");
            return response;
        }
    }
}
