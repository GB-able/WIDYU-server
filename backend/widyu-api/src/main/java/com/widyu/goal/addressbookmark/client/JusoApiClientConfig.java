package com.widyu.goal.addressbookmark.client;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

public class JusoApiClientConfig {

    @Bean
    Logger.Level jusoFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Decoder jusoFeignDecoder() {
        ObjectFactory<HttpMessageConverters> messageConverters = () ->
                new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
        return new SpringDecoder(messageConverters);
    }

    @Bean
    public RequestInterceptor jusoRequestInterceptor() {
        return requestTemplate -> requestTemplate.header("Accept", "application/json");
    }
}
