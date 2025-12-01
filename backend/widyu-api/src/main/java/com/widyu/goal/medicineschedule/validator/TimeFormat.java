package com.widyu.goal.medicineschedule.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 시간 형식 검증 어노테이션 (HH:mm 형식)
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeFormatValidator.class)
public @interface TimeFormat {

    String message() default "알람 시간은 HH:mm 형식이어야 합니다. (예: 08:30, 14:00)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
