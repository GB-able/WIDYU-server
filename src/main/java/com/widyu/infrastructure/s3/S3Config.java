package com.widyu.infrastructure.s3;

import com.widyu.global.properties.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                s3Properties.credentials().accessKey(),
                s3Properties.credentials().secretKey()
        );
        
        return S3Client.builder()
                .region(Region.of(s3Properties.region().statics()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }
}
