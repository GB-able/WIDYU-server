package com.widyu.goal.medicineschedule.dto.response;

import java.util.List;

public record MedicineMonthlyResponse(
        Integer lastMonthAchieveCount,
        Integer currentMonthAchieveCount,
        List<Double> monthlyGoalRates
) {}
