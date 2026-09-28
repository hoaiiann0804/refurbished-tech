package com.example.refurbished;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RefurbishedApplication {

    public static void main(String[] args) {
        SpringApplication.run(RefurbishedApplication.class, args);
    }
}
