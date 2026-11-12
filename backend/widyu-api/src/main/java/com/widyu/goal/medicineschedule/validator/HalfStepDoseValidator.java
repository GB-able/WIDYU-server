package com.widyu.goal.medicineschedule.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class HalfStepDoseValidator implements ConstraintValidator<HalfStepDose, Double> {

    @Override
    public boolean isValid(Double value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null 체크는 @NotNull에서 처리
        }
        // 0.5의 배수는 이진 부동소수로 정확히 표현되므로 나머지 비교가 정확하다.
        return Double.isFinite(value) && value % 0.5 == 0;
    }
}
