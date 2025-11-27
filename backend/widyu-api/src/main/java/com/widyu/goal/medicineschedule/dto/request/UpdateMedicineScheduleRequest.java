package com.widyu.goal.medicineschedule.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdateMedicineScheduleRequest(
        @NotBlank(message = "알람 시간은 필수입니다.")
        String alarmTime,

        @NotNull(message = "카테고리는 필수입니다.")
        @NotEmpty(message = "최소 하나의 카테고리가 필요합니다.")
        @Valid
        List<CategoryItem> categories
) {
    public record CategoryItem(
            Long categoryId,

            @NotBlank(message = "카테고리 이름은 필수입니다.")
            String name,

            @Valid
            List<MedicineItem> medicines
    ) {}

    public record MedicineItem(
            @NotNull(message = "약품 ID는 필수입니다.")
            @Positive(message = "약품 ID는 양수여야 합니다.")
            Long medicineId,

            @NotBlank(message = "약품 이름은 필수입니다.")
            String medicineName,

            @NotNull(message = "복용량은 필수입니다.")
            @Positive(message = "복용량은 양수여야 합니다.")
            Double dose,

            String unit,

            String imageUrl,

            String description
    ) {}
}
