package com.widyu.global.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = {
        "com.widyu.pay.config",
        "com.widyu.goal.medicineschedule.client"
})
public class FeignConfig {
}
