package com.widyu.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medicine")
public record MedicineProperties(
        Api api
) {
    public record Api(
            String url,
            String serviceKey
    ) {}
}