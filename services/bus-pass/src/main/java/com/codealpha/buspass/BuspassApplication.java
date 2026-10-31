package com.codealpha.buspass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BuspassApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuspassApplication.class, args);
    }
}
