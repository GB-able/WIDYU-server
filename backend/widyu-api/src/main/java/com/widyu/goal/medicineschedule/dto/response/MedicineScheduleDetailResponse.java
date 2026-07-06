package com.widyu.goal.medicineschedule.dto.response;

import com.widyu.medicine.MedicineCategory;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.medicine.MedicineScheduleDetail;
import java.util.List;
import java.util.stream.Collectors;

public record MedicineScheduleDetailResponse(
        String alarmTime,
        Double totalCount,
        List<CategoryItem> categories
) {
    public static MedicineScheduleDetailResponse from(MedicineSchedule schedule) {
        List<CategoryItem> categories = schedule.getCategories().stream()
                .map(CategoryItem::from)
                .collect(Collectors.toList());

        return new MedicineScheduleDetailResponse(
                schedule.getAlarmTime().toString(),
                schedule.getTotalCount().doubleValue(),
                categories
        );
    }

    public record CategoryItem(
            Long categoryId,
            String name,
            Double countSum,
            List<MedicineItem> medicines
    ) {
        public static CategoryItem from(MedicineCategory category) {
            List<MedicineItem> medicines = category.getMedicines().stream()
                    .map(MedicineItem::from)
                    .collect(Collectors.toList());

            return new CategoryItem(
                    category.getId(),
                    category.getName(),
                    category.getCountSum().doubleValue(),
                    medicines
            );
        }
    }

    public record MedicineItem(
            Long medicineId,
            String medicineName,
            Double dose,
            String imageUrl,
            String description
    ) {
        public static MedicineItem from(MedicineScheduleDetail detail) {
            return new MedicineItem(
                    detail.getMedicine().getId(),
                    detail.getMedicine().getName(),
                    detail.getDose().doubleValue(),
                    detail.getMedicine().getImageUrl(),
                    detail.getMedicine().getDescription()
            );
        }
    }
}
