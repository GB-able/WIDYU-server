package com.widyu.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ffprobe")
public record FFprobeProperties(
        String path
) {
}