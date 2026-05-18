package com.widyu.admin.dto.response;

import com.widyu.auth.dto.response.TokenPairResponse;

public record AdminLoginResponse(TokenPairResponse result) {

    public static AdminLoginResponse of(TokenPairResponse tokenPair) {
        return new AdminLoginResponse(tokenPair);
    }
}
