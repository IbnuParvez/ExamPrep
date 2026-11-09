package com.examprep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the ExamPrep application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ExamPrepApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamPrepApplication.class, args);
    }
}
