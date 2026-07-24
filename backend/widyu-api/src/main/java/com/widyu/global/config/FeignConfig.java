package com.widyu.global.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = {
        "com.widyu.pay",
        "com.widyu.goal.medicineschedule.client",
        "com.widyu.goal.addressbookmark.client"
})
public class FeignConfig {
}
