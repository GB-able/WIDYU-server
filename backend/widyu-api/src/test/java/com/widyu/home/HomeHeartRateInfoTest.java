package com.widyu.home;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import com.widyu.home.dto.response.SeniorHomeCardsResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("홈 심박수 착용 상태 테스트")
class HomeHeartRateInfoTest {

    @Test
    @DisplayName("최근 30초 이내 심박 데이터는 착용 중으로 판단하고 BPM을 반환한다")
    void 최근_30초_이내_심박데이터는_착용중() {
        HeartRateResult result = HeartRateResult.of(
                1L,
                HeartRateStatus.NORMAL,
                72,
                LocalDateTime.now().minusSeconds(10)
        );

        SeniorHomeCardsResponse.HeartRateInfo seniorInfo = SeniorHomeCardsResponse.HeartRateInfo.from(result);
        GuardianHomeCardsResponse.HeartRateInfo guardianInfo = GuardianHomeCardsResponse.HeartRateInfo.from(result);

        assertThat(seniorInfo.isWearing()).isTrue();
        assertThat(seniorInfo.bpm()).isEqualTo(72);
        assertThat(guardianInfo.isWearing()).isTrue();
        assertThat(guardianInfo.bpm()).isEqualTo(72);
    }

    @Test
    @DisplayName("최근 30초를 넘은 심박 데이터는 미착용으로 판단하고 BPM을 반환하지 않는다")
    void 최근_30초_초과_심박데이터는_미착용() {
        HeartRateResult result = HeartRateResult.of(
                1L,
                HeartRateStatus.NORMAL,
                72,
                LocalDateTime.now().minusSeconds(31)
        );

        SeniorHomeCardsResponse.HeartRateInfo seniorInfo = SeniorHomeCardsResponse.HeartRateInfo.from(result);
        GuardianHomeCardsResponse.HeartRateInfo guardianInfo = GuardianHomeCardsResponse.HeartRateInfo.from(result);

        assertThat(seniorInfo.isWearing()).isFalse();
        assertThat(seniorInfo.heartRateStatus()).isEqualTo(HeartRateStatus.UNKNOWN);
        assertThat(seniorInfo.bpm()).isNull();
        assertThat(guardianInfo.isWearing()).isFalse();
        assertThat(guardianInfo.heartRateStatus()).isEqualTo(HeartRateStatus.UNKNOWN);
        assertThat(guardianInfo.bpm()).isNull();
    }
}
