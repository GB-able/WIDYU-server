package com.widyu.goal.medicineschedule.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 복용량이 0.5정 단위인지 검증한다 (#1001). 양수 여부는 {@code @Positive}가 본다.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HalfStepDoseValidator.class)
public @interface HalfStepDose {

    String message() default "복용량은 0.5 단위로 입력해 주세요. (예: 1, 1.5, 2.5)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
