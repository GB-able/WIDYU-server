package com.widyu.global.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Value("${ai.server.connect-timeout:5000}")
    private int aiConnectTimeout;

    @Value("${ai.server.read-timeout:10000}")
    private int aiReadTimeout;

    @Bean
    public RestClient restClient() {
        RestTemplate restTemplate =
                new RestTemplateBuilder()
                        .setConnectTimeout(Duration.ofSeconds(10))
                        .setReadTimeout(Duration.ofSeconds(5))
                        .build();

        return RestClient.create(restTemplate);
    }

    @Bean(name = "aiRestTemplate")
    public RestTemplate aiRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(aiConnectTimeout))
                .setReadTimeout(Duration.ofMillis(aiReadTimeout))
                .build();
    }
}
