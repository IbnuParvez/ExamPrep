package com.examprep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ExamPrep entry point: a modular monolith that serves the student interface and the AI
 * study engine from a single Spring Boot application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ExamPrepApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamPrepApplication.class, args);
    }
}
