package com.abv.hrerpisapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class HrErpIsapiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HrErpIsapiApplication.class, args);
    }
}