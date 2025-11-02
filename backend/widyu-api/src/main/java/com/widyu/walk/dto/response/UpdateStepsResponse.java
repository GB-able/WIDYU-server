package com.widyu.walk.dto.response;

public record UpdateStepsResponse(
        boolean achieved
) {
    public static UpdateStepsResponse of(boolean achieved) {
        return new UpdateStepsResponse(achieved);
    }
}
