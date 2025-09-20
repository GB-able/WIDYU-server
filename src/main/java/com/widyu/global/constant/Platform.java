package com.widyu.global.constant;

import lombok.Getter;

@Getter
public enum Platform {
    IOS("ios"),
    ANDROID("android");

    private final String value;

    Platform(String value) {
        this.value = value;
    }

    public static Platform from(String value) {
        if (value == null) {
            return IOS; // 기본값
        }

        for (Platform platform : Platform.values()) {
            if (platform.value.equalsIgnoreCase(value)) {
                return platform;
            }
        }
        return IOS;
    }
}