package com.widyu.heart.dto.response;

public record HeartGraphPageResponse(
        HeartGraphResponse heartGraph,
        EmergencyHistoryResponse emergencyHistory
) {
    public static HeartGraphPageResponse of(HeartGraphResponse heartGraph, EmergencyHistoryResponse emergencyHistory) {
        return new HeartGraphPageResponse(heartGraph, emergencyHistory);
    }
}
