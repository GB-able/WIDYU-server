package com.widyu.fcm.api.dto.response;

import lombok.Builder;

@Builder
public record UnviewedCountResponse(
        long unviewedCount
) {
}