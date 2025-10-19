package com.widyu.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws")
public record S3Properties(
        Credentials credentials,
        Region region,
        S3 s3
) {
    public record Credentials(
            String accessKey,
            String secretKey
    ) {}
    
    public record Region(
            String statics
    ) {}
    
    public record S3(
            String bucketName,
            String baseUrl
    ) {}
}