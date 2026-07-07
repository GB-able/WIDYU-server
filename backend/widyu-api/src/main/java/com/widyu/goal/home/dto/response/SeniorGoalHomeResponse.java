package com.widyu.goal.home.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record SeniorGoalHomeResponse(
        @Schema(description = "약 스케줄 정보")
        MedicineInfo medicine,

        @Schema(description = "걸음 수 정보")
        StepsInfo steps,

        @Schema(description = "병원 일정 정보")
        HospitalInfo hospital
) {
    @Schema(description = "약 스케줄 정보")
    public record MedicineInfo(
            @Schema(description = "약 스케줄 ID", example = "1")
            Long medicineScheduleId,

            @Schema(description = "오늘 복용한 횟수", example = "2")
            Integer takenCount,

            @Schema(description = "오늘 총 복용 예정 횟수", example = "3")
            Integer totalCount,

            @Schema(description = "다음 복용 예정 개수", example = "4")
            Integer nextDoseCount,

            @Schema(description = "다음 알람 시간", example = "17:00")
            String nextAlarmTime
    ) {
    }

    @Schema(description = "걸음 수 정보")
    public record StepsInfo(
            @Schema(description = "오늘 걸음 수", example = "9829")
            Integer steps,

            @Schema(description = "목표 걸음 수", example = "10000")
            Integer goal
    ) {
    }

    @Schema(description = "병원 일정 정보")
    public record HospitalInfo(
            @Schema(description = "병원 일정 ID", example = "1")
            Long hospitalScheduleId,

            @Schema(description = "D-day", example = "14")
            Integer dday,

            @Schema(description = "병원 방문 일시", example = "2025-08-26T17:00:00")
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime datetime,

            @Schema(description = "병원 이름", example = "고려대학교 의과대학 부속병원")
            String name,

            @Schema(description = "병원 주소", example = "병원 주소")
            String address
    ) {
    }
}
