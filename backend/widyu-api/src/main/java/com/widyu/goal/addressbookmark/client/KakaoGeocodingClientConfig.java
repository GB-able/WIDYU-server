package com.widyu.goal.addressbookmark.client;

import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class KakaoGeocodingClientConfig {

    @Value("${oauth.kakao.admin-key}")
    private String adminKey;

    @Bean
    Logger.Level kakaoGeocodingFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public RequestInterceptor kakaoGeocodingRequestInterceptor() {
        return requestTemplate -> requestTemplate.header("Authorization", "KakaoAK " + adminKey);
    }
}
