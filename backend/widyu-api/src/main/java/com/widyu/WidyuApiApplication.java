package com.widyu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "com.widyu.pay.config")
public class WidyuApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(WidyuApiApplication.class, args);
    }
}