// com.widyu.auth.dto.request.common.LogoutRequest.java
package com.widyu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {}
