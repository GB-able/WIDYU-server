package com.widyu.global.config;

import com.widyu.global.properties.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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

    // S3Presigner는 build 시점에 region으로 endpoint를 즉시 검증하므로,
    // AWS 환경변수가 없는 테스트 환경에서 컨텍스트 로딩이 깨지지 않도록 지연 생성한다.
    @Bean
    @Lazy
    public S3Presigner s3Presigner() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                s3Properties.credentials().accessKey(),
                s3Properties.credentials().secretKey()
        );

        return S3Presigner.builder()
                .region(Region.of(s3Properties.region().statics()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }
}
