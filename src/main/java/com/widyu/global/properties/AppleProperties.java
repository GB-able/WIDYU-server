package com.widyu.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth.apple")
public record AppleProperties(
        String iosClientId,
        String androidClientId,
        String teamId,
        String keyId,
        String privateKey,
        String redirectUri
) {
}