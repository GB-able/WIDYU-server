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
    @DisplayName("AI가 거부하는 심박수 0과 300은 요청 단계에서 거절한다")
    void AI가_거부하는_심박수_0과_300은_요청단계에서_거절한다() {
        // AI는 bpm 0·300에 400을 반환한다. 서버가 통과시키면 배치 15개 전체가 저장되지 않는다.
        assertThat(validator.validate(request(0, "REST"))).isNotEmpty();
        assertThat(validator.validate(request(300, "REST"))).isNotEmpty();
        assertThat(validator.validate(request(301, "REST"))).isNotEmpty();
    }

    @Test
    @DisplayName("AI가 허용하는 심박수 경계값 1과 299는 통과시킨다")
    void AI가_허용하는_심박수_경계값_1과_299는_통과시킨다() {
        assertThat(validator.validate(request(1, "REST"))).isEmpty();
        assertThat(validator.validate(request(299, "REST"))).isEmpty();
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
