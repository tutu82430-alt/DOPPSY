package com.doppsy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Doppsy Healthcare Concierge backend.
 *
 * <p>Start the application with:
 * <pre>
 *   # 1. Spin up PostgreSQL
 *   docker-compose up -d
 *
 *   # 2. Run the Spring Boot app
 *   ./mvnw spring-boot:run
 * </pre>
 * </p>
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class DoppsyApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoppsyApplication.class, args);
    }
}
