package com.widyu.goal.medicineschedule.dto.response;

public record MedicationProofResponse(
        Long currentPoints,
        Long earnedPoints
) {
    public static MedicationProofResponse of(Long currentPoints, Long earnedPoints) {
        return new MedicationProofResponse(currentPoints, earnedPoints);
    }
}
