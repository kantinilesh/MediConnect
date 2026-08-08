package com.mediconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * MediConnect — Healthcare appointment booking microservice.
 *
 * <p>Entry point for the Spring Boot application.
 * Business logic is implemented in subsequent phases.
 */
@SpringBootApplication
@EnableJpaAuditing
public class MediConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediConnectApplication.class, args);
    }
}
