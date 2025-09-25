package com.widyu.domain.fcm.api.dto.response;

import lombok.Builder;

@Builder
public record UnviewedCountResponse(
        long unviewedCount
) {
}