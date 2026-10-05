package com.maito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for the Maito E-Commerce Modular Monolith.
 */
@SpringBootApplication
public class MaitoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaitoApplication.class, args);
    }
}