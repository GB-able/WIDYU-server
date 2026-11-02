package com.widyu.mypage.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.auth.dto.request.SeniorSignUpRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("시니어 주소 요청의 위도·경도 검증 (#951)")
class SeniorCoordinateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("위도와 경도를 둘 다 생략하거나 범위 안에서 함께 보내면 통과한다")
    void 위도와_경도를_둘_다_생략하거나_함께_보내면_통과한다() {
        // when / then
        assertThat(validator.validate(address(null, null))).isEmpty();
        assertThat(validator.validate(address(37.5611, 126.8379))).isEmpty();
        assertThat(validator.validate(signUp(null, null))).isEmpty();
        assertThat(validator.validate(signUp(37.4979, 127.0276))).isEmpty();
    }

    @Test
    @DisplayName("위도나 경도 중 하나만 보내면 함께 보내라는 오류가 난다")
    void 위도나_경도_중_하나만_보내면_오류가_난다() {
        // when
        Set<ConstraintViolation<UpdateSeniorAddressRequest>> latitudeOnly = validator.validate(address(37.5611, null));
        Set<ConstraintViolation<SeniorSignUpRequest>> longitudeOnly = validator.validate(signUp(null, 127.0276));

        // then
        assertThat(latitudeOnly).extracting(ConstraintViolation::getMessage)
                .containsExactly("위도와 경도는 함께 보내거나 둘 다 생략해야 합니다.");
        assertThat(longitudeOnly).extracting(ConstraintViolation::getMessage)
                .containsExactly("위도와 경도는 함께 보내거나 둘 다 생략해야 합니다.");
    }

    @Test
    @DisplayName("위도나 경도가 범위를 벗어나면 범위 오류가 난다")
    void 위도나_경도가_범위를_벗어나면_오류가_난다() {
        // when / then
        assertThat(validator.validate(address(90.1, 126.8379))).extracting(ConstraintViolation::getMessage)
                .containsExactly("위도는 -90 이상 90 이하여야 합니다.");
        assertThat(validator.validate(signUp(37.4979, -180.5))).extracting(ConstraintViolation::getMessage)
                .containsExactly("경도는 -180 이상 180 이하여야 합니다.");
    }

    private static UpdateSeniorAddressRequest address(Double latitude, Double longitude) {
        return new UpdateSeniorAddressRequest("서울시 강서구", latitude, longitude);
    }

    private static SeniorSignUpRequest signUp(Double latitude, Double longitude) {
        return new SeniorSignUpRequest("오일남", LocalDate.of(1950, 1, 1), "01011112222",
                "서울시 강남구", "1234567", latitude, longitude);
    }
}
