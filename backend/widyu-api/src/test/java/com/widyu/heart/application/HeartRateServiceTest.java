package com.widyu.heart.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.heart.repository.HeartRateEventRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeartRateService 예외 처리 단위 테스트")
class HeartRateServiceTest {

    @Mock private HeartRateAnomalyDetector heartRateAnomalyDetector;
    @Mock private HeartRateResultRepository heartRateResultRepository;
    @Mock private HeartRateEventRepository heartRateEventRepository;
    @Mock private HeartRateEmergencyRepository heartRateEmergencyRepository;
    @Mock private MemberRepository memberRepository;

    @InjectMocks
    private HeartRateService heartRateService;

    @Test
    @DisplayName("심박수 처리 대상 회원이 없으면 MEMBER_NOT_FOUND 예외를 던지고 분석/저장을 하지 않는다")
    void 심박수_처리_대상_회원이_없으면_예외가_발생한다() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> heartRateService.processHeartRates(1L, request()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
        then(heartRateAnomalyDetector).should(never()).detectAnomaly(any());
        then(heartRateResultRepository).should(never()).save(any(HeartRateResult.class));
        then(heartRateEventRepository).should(never()).saveAll(any());
    }

    private HeartRateSendRequest request() {
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(70 + i, LocalDateTime.now().plusSeconds(i)))
                .toList();
        return new HeartRateSendRequest(measurements, "서울시");
    }
}
