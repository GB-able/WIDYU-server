package com.widyu.heart.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HeartRateSendRequest 검증 테스트")
class HeartRateSendRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("활동 상태가 공백이면 UNKNOWN으로 정규화하고 허용값이 아니면 거절한다")
    void 활동상태가_공백이면_UNKNOWN으로_정규화하고_허용값이_아니면_거절한다() {
        HeartRateSendRequest blankContext = request(70, " ");
        HeartRateSendRequest nullContext = request(70, null);
        HeartRateSendRequest invalidContext = request(70, "SLEEP");

        assertThat(blankContext.normalizedContext()).isEqualTo("UNKNOWN");
        assertThat(nullContext.normalizedContext()).isEqualTo("UNKNOWN");
        assertThat(validator.validate(blankContext)).isEmpty();
        assertThat(validator.validate(nullContext)).isEmpty();
        assertThat(validator.validate(invalidContext)).isNotEmpty();
    }

    @Test
    @DisplayName("심박수는 0을 허용하고 300을 초과하면 거절한다")
    void 심박수는_0을_허용하고_300을_초과하면_거절한다() {
        assertThat(validator.validate(request(0, "REST"))).isEmpty();
        assertThat(validator.validate(request(301, "REST"))).isNotEmpty();
    }

    private HeartRateSendRequest request(int firstHeartRate, String context) {
        LocalDateTime measuredAt = LocalDateTime.of(2026, 7, 26, 12, 0);
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> {
                    if (i == 0) {
                        return new HeartRateMeasurement(firstHeartRate, measuredAt);
                    }
                    return new HeartRateMeasurement(70, measuredAt.plusSeconds(i));
                })
                .toList();
        return HeartRateSendRequest.of(measurements, "서울시", context);
    }
}
