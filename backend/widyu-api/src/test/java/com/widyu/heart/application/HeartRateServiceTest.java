package com.widyu.heart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.dto.response.HeartGraphPageResponse;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.heart.repository.HeartRateEventRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
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

    @Test
    @DisplayName("최근 심박수 조회 시 최신 결과가 없으면 마지막 이벤트 값을 반환한다")
    void 최근심박수_조회시_최신결과가_없으면_마지막이벤트를_반환한다() {
        Long memberId = 1L;
        LocalDateTime measuredAt = LocalDateTime.now().minusSeconds(31);
        HeartRateEvent latestEvent = heartRateEvent(78, measuredAt, HeartRateStatus.NORMAL);

        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(Optional.of(latestEvent));

        HeartRateStatusResponse response = heartRateService.getHeartRateStatus(memberId);

        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.heartRateStatus()).isEqualTo(HeartRateStatus.NORMAL);
        assertThat(response.heartRate()).isEqualTo(78);
        assertThat(response.measuredAt()).isEqualTo(measuredAt);
    }

    @Test
    @DisplayName("그래프 갱신 시 최신 결과가 없으면 최근 이벤트 중 마지막 값을 현재 심박수로 반환한다")
    void 그래프갱신시_최신결과가_없으면_최근이벤트_마지막값을_현재심박수로_반환한다() {
        Long memberId = 1L;
        LocalDateTime firstMeasuredAt = LocalDateTime.now().minusSeconds(45);
        LocalDateTime lastMeasuredAt = LocalDateTime.now().minusSeconds(31);
        HeartRateEvent firstEvent = heartRateEvent(75, firstMeasuredAt, HeartRateStatus.NORMAL);
        HeartRateEvent latestEvent = heartRateEvent(82, lastMeasuredAt, HeartRateStatus.ANOMALY);

        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findMaxHeartRateByMemberId(memberId)).willReturn(Optional.of(82));
        given(heartRateEventRepository.findMinHeartRateByMemberId(memberId)).willReturn(Optional.of(75));
        given(heartRateEventRepository.findTop5ByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(latestEvent, firstEvent));
        given(heartRateEmergencyRepository.countByMemberId(memberId)).willReturn(0L);
        given(heartRateEventRepository.findTotalDurationMinutesByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(List.of());

        HeartGraphPageResponse response = heartRateService.getHeartGraphRefresh(memberId);

        assertThat(response.heartGraph().current().heartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().status()).isEqualTo(HeartRateStatus.ANOMALY);
        assertThat(response.heartGraph().current().maxHeartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().minHeartRate()).isEqualTo(75);
    }

    @Test
    @DisplayName("그래프 최초 조회 시 최신 결과가 없으면 전체 이벤트 중 마지막 값을 현재 심박수로 반환한다")
    void 그래프최초조회시_최신결과가_없으면_전체이벤트_마지막값을_현재심박수로_반환한다() {
        Long memberId = 1L;
        LocalDateTime firstMeasuredAt = LocalDateTime.now().minusSeconds(45);
        LocalDateTime lastMeasuredAt = LocalDateTime.now().minusSeconds(31);
        HeartRateEvent firstEvent = heartRateEvent(75, firstMeasuredAt, HeartRateStatus.NORMAL);
        HeartRateEvent latestEvent = heartRateEvent(82, lastMeasuredAt, HeartRateStatus.ANOMALY);

        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findByMemberIdOrderByMeasuredAtAsc(memberId))
                .willReturn(List.of(firstEvent, latestEvent));
        given(heartRateEventRepository.findMaxHeartRateByMemberId(memberId)).willReturn(Optional.of(82));
        given(heartRateEventRepository.findMinHeartRateByMemberId(memberId)).willReturn(Optional.of(75));
        given(heartRateEmergencyRepository.findFirstByMemberIdOrderByMeasuredAtAsc(memberId))
                .willReturn(Optional.empty());
        given(heartRateEmergencyRepository.countByMemberId(memberId)).willReturn(0L);
        given(heartRateEventRepository.findTotalDurationMinutesByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(List.of());

        HeartGraphPageResponse response = heartRateService.getHeartGraph(memberId);

        assertThat(response.heartGraph().current().heartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().measuredAt()).isEqualTo(lastMeasuredAt);
        assertThat(response.heartGraph().current().status()).isEqualTo(HeartRateStatus.ANOMALY);
        assertThat(response.heartGraph().current().maxHeartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().minHeartRate()).isEqualTo(75);
    }

    private HeartRateSendRequest request() {
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(70 + i, LocalDateTime.now().plusSeconds(i)))
                .toList();
        return new HeartRateSendRequest(measurements, "서울시");
    }

    private HeartRateEvent heartRateEvent(Integer heartRate, LocalDateTime measuredAt, HeartRateStatus status) {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        return HeartRateEvent.of(member, heartRate, measuredAt, status);
    }
}
