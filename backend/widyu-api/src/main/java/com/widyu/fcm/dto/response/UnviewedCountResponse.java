package com.widyu.fcm.dto.response;

import lombok.Builder;

@Builder
public record UnviewedCountResponse(
        long unviewedCount
) {
}