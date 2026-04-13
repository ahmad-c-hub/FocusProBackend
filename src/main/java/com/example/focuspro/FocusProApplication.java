package com.example.focuspro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FocusProApplication {

    public static void main(String[] args) {
        SpringApplication.run(FocusProApplication.class, args);
    }

}
