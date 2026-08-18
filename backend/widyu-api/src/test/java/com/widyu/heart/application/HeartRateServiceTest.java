package com.widyu.heart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.fcm.event.heart.dto.HeartRateEmergencyEvent;
import com.widyu.heart.HeartRateEmergency;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.application.HeartRateAnomalyDetector.DetectionResult;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.dto.response.HeartGraphPageResponse;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import com.widyu.heart.dto.response.RecentEmergencyResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeartRateService 예외 처리 단위 테스트")
class HeartRateServiceTest {

    @Mock private HeartRateAnomalyDetector heartRateAnomalyDetector;
    @Mock private HeartRatePersistenceService heartRatePersistenceService;
    @Mock private ApplicationEventPublisher eventPublisher;
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
        then(heartRateAnomalyDetector).should(never()).detect(anyLong(), any(), any());
        then(heartRatePersistenceService).should(never())
                .saveAnalysis(anyLong(), any(), any(HeartRateStatus.class), anyBoolean());
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
    @DisplayName("진행 중인 위급 사이클이 없으면 그래프 최초 조회는 빈 이벤트를 반환한다")
    void 위급사이클이_없으면_그래프최초조회는_빈_이벤트를_반환한다() {
        // given
        Long memberId = 1L;
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(30);
        HeartRateEvent latestEvent = heartRateEvent(82, LocalDateTime.now().minusSeconds(31), HeartRateStatus.NORMAL);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(heartRateEmergency(170, expiredAt)));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(Optional.of(latestEvent));

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraph(memberId);

        // then
        assertThat(response.heartGraph().events()).isEmpty();
        assertThat(response.heartGraph().current().heartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().maxHeartRate()).isNull();
        assertThat(response.emergencyHistory().events()).hasSize(1);
        then(heartRateEventRepository).should(never())
                .findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(anyLong(), any());
    }

    @Test
    @DisplayName("위급 이력의 지속 시간은 첫 감지부터 마지막 감지까지의 분이다")
    void 위급이력의_지속시간은_첫_감지부터_마지막_감지까지다() {
        // given
        Long memberId = 1L;
        LocalDateTime firstDetectedAt = LocalDateTime.now().minusMinutes(11);
        LocalDateTime secondDetectedAt = LocalDateTime.now().minusMinutes(7);
        LocalDateTime lastDetectedAt = LocalDateTime.now().minusMinutes(3);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(
                        heartRateEmergency(190, lastDetectedAt),
                        heartRateEmergency(185, secondDetectedAt),
                        heartRateEmergency(180, firstDetectedAt)
                ));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(List.of());
        given(heartRateEventRepository.findMaxHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findMinHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(Optional.empty());
        given(heartRateEmergencyRepository.findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(Optional.empty());

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraph(memberId);

        // then
        assertThat(response.emergencyHistory().totalDuration()).isEqualTo(8);
        assertThat(response.emergencyHistory().emergencyCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("진행 중인 사이클이 없으면 위급 이력의 지속 시간은 0이다")
    void 진행중인_사이클이_없으면_지속시간은_0이다() {
        // given
        Long memberId = 1L;
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(30);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(heartRateEmergency(170, expiredAt)));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(Optional.empty());

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraph(memberId);

        // then
        assertThat(response.emergencyHistory().totalDuration()).isZero();
        assertThat(response.emergencyHistory().emergencyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("위급 기록이 한 번도 없으면 그래프 최초 조회는 빈 이벤트를 반환한다")
    void 위급기록이_없으면_그래프최초조회는_빈_이벤트를_반환한다() {
        // given
        Long memberId = 1L;

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(List.of());
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(Optional.empty());

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraph(memberId);

        // then
        assertThat(response.heartGraph().events()).isEmpty();
        assertThat(response.heartGraph().current().status()).isEqualTo(HeartRateStatus.UNKNOWN);
    }

    @Test
    @DisplayName("그래프 최초 조회는 사이클 시작 5분 전부터의 이벤트를 반환한다")
    void 그래프최초조회는_사이클시작_5분전부터의_이벤트를_반환한다() {
        // given
        Long memberId = 1L;
        LocalDateTime cycleStart = LocalDateTime.now().minusMinutes(2);
        LocalDateTime lastMeasuredAt = LocalDateTime.now().minusSeconds(31);
        HeartRateEvent firstEvent = heartRateEvent(75, LocalDateTime.now().minusSeconds(45), HeartRateStatus.NORMAL);
        HeartRateEvent latestEvent = heartRateEvent(82, lastMeasuredAt, HeartRateStatus.EMERGENCY);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(heartRateEmergency(180, cycleStart)));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(List.of(firstEvent, latestEvent));
        given(heartRateEventRepository.findMaxHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.of(82));
        given(heartRateEventRepository.findMinHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.of(75));
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(Optional.of(latestEvent));
        given(heartRateEmergencyRepository.findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(Optional.empty());

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraph(memberId);

        // then
        assertThat(response.heartGraph().events()).hasSize(2);
        assertThat(response.heartGraph().current().heartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().measuredAt()).isEqualTo(lastMeasuredAt);
        assertThat(response.heartGraph().current().maxHeartRate()).isEqualTo(82);

        ArgumentCaptor<LocalDateTime> windowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(heartRateEventRepository).should()
                .findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), windowCaptor.capture());
        assertThat(windowCaptor.getValue()).isEqualTo(cycleStart.minusMinutes(5));
    }

    @Test
    @DisplayName("위급 기록이 5분 이내 간격으로 이어지면 사이클 시작은 가장 이른 감지 시각이 된다")
    void 위급기록이_5분이내로_이어지면_사이클시작은_가장_이른_감지시각이다() {
        // given
        Long memberId = 1L;
        LocalDateTime firstDetectedAt = LocalDateTime.now().minusMinutes(11);
        LocalDateTime secondDetectedAt = LocalDateTime.now().minusMinutes(7);
        LocalDateTime lastDetectedAt = LocalDateTime.now().minusMinutes(3);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(
                        heartRateEmergency(190, lastDetectedAt),
                        heartRateEmergency(185, secondDetectedAt),
                        heartRateEmergency(180, firstDetectedAt)
                ));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(List.of());
        given(heartRateEventRepository.findMaxHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findMinHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(Optional.empty());
        given(heartRateEmergencyRepository.findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(Optional.empty());

        // when
        heartRateService.getHeartGraph(memberId);

        // then
        ArgumentCaptor<LocalDateTime> windowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(heartRateEventRepository).should()
                .findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), windowCaptor.capture());
        assertThat(windowCaptor.getValue()).isEqualTo(firstDetectedAt.minusMinutes(5));
    }

    @Test
    @DisplayName("위급 기록 간격이 5분을 넘으면 이전 사이클은 조회 범위에서 제외한다")
    void 위급기록_간격이_5분을_넘으면_이전_사이클은_제외한다() {
        // given
        Long memberId = 1L;
        LocalDateTime previousCycleAt = LocalDateTime.now().minusMinutes(40);
        LocalDateTime currentCycleAt = LocalDateTime.now().minusMinutes(2);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(
                        heartRateEmergency(190, currentCycleAt),
                        heartRateEmergency(180, previousCycleAt)
                ));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(List.of());
        given(heartRateEventRepository.findMaxHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findMinHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(Optional.empty());
        given(heartRateEmergencyRepository.findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(Optional.empty());

        // when
        heartRateService.getHeartGraph(memberId);

        // then
        ArgumentCaptor<LocalDateTime> windowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(heartRateEventRepository).should()
                .findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), windowCaptor.capture());
        assertThat(windowCaptor.getValue()).isEqualTo(currentCycleAt.minusMinutes(5));
    }

    @Test
    @DisplayName("그래프 갱신 시 최신 결과가 없으면 가장 최근 이벤트를 현재 심박수로 반환하고 측정 시각도 포함한다")
    void 그래프갱신시_최신결과가_없으면_최근이벤트를_현재심박수로_반환한다() {
        // given
        Long memberId = 1L;
        LocalDateTime cycleStart = LocalDateTime.now().minusMinutes(2);
        LocalDateTime firstMeasuredAt = LocalDateTime.now().minusSeconds(45);
        LocalDateTime lastMeasuredAt = LocalDateTime.now().minusSeconds(31);
        HeartRateEvent firstEvent = heartRateEvent(75, firstMeasuredAt, HeartRateStatus.NORMAL);
        HeartRateEvent latestEvent = heartRateEvent(82, lastMeasuredAt, HeartRateStatus.ANOMALY);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(heartRateEmergency(180, cycleStart)));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findMaxHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.of(82));
        given(heartRateEventRepository.findMinHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.of(75));
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(Optional.of(latestEvent));
        given(heartRateEventRepository.findTop5ByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(latestEvent, firstEvent));

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraphRefresh(memberId, null);

        // then
        assertThat(response.heartGraph().current().heartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().measuredAt()).isEqualTo(lastMeasuredAt);
        assertThat(response.heartGraph().current().status()).isEqualTo(HeartRateStatus.ANOMALY);
        assertThat(response.heartGraph().current().maxHeartRate()).isEqualTo(82);
        assertThat(response.heartGraph().current().minHeartRate()).isEqualTo(75);
    }

    @Test
    @DisplayName("그래프 갱신에 since를 넘기면 그 이후 신규 이벤트만 반환한다")
    void 그래프갱신에_since를_넘기면_신규이벤트만_반환한다() {
        // given
        Long memberId = 1L;
        LocalDateTime cycleStart = LocalDateTime.now().minusMinutes(2);
        LocalDateTime since = LocalDateTime.now().minusSeconds(20);
        LocalDateTime newMeasuredAt = LocalDateTime.now().minusSeconds(5);
        HeartRateEvent newEvent = heartRateEvent(90, newMeasuredAt, HeartRateStatus.NORMAL);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(heartRateEmergency(180, cycleStart)));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findMaxHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.of(90));
        given(heartRateEventRepository.findMinHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.of(70));
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(Optional.of(newEvent));
        given(heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(memberId, since))
                .willReturn(List.of(newEvent));

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraphRefresh(memberId, since);

        // then
        assertThat(response.heartGraph().events()).hasSize(1);
        assertThat(response.heartGraph().events().getFirst().measuredAt()).isEqualTo(newMeasuredAt);
        then(heartRateEventRepository).should(never()).findTop5ByMemberIdOrderByMeasuredAtDesc(anyLong());
    }

    @Test
    @DisplayName("그래프 갱신 since가 사이클 조회 범위보다 과거이면 범위 시작점으로 잘라 조회한다")
    void 그래프갱신_since가_사이클범위보다_과거이면_범위시작점으로_잘라_조회한다() {
        // given
        Long memberId = 1L;
        LocalDateTime cycleStart = LocalDateTime.now().minusMinutes(2);
        LocalDateTime tooOldSince = LocalDateTime.now().minusDays(3);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId))
                .willReturn(List.of(heartRateEmergency(180, cycleStart)));
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findMaxHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findMinHeartRateByMemberIdSince(eq(memberId), any())).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), any()))
                .willReturn(List.of());

        // when
        heartRateService.getHeartGraphRefresh(memberId, tooOldSince);

        // then
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(heartRateEventRepository).should()
                .findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(eq(memberId), sinceCaptor.capture());
        assertThat(sinceCaptor.getValue()).isEqualTo(cycleStart.minusMinutes(5));
    }

    @Test
    @DisplayName("진행 중인 위급 사이클이 없으면 그래프 갱신도 빈 이벤트를 반환한다")
    void 위급사이클이_없으면_그래프갱신도_빈_이벤트를_반환한다() {
        // given
        Long memberId = 1L;
        LocalDateTime since = LocalDateTime.now().minusSeconds(20);

        given(heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(List.of());
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.empty());
        given(heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)).willReturn(Optional.empty());

        // when
        HeartGraphPageResponse response = heartRateService.getHeartGraphRefresh(memberId, since);

        // then
        assertThat(response.heartGraph().events()).isEmpty();
        then(heartRateEventRepository).should(never())
                .findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(anyLong(), any());
    }

    @Test
    @DisplayName("위험 사이클 안에 위급 기록이 있으면 감지됨과 해당 기록을 반환한다")
    void 위험사이클_안에_위급기록이_있으면_감지됨을_반환한다() {
        // given
        Long memberId = 1L;
        LocalDateTime measuredAt = LocalDateTime.now().minusMinutes(3);
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        HeartRateEmergency emergency = HeartRateEmergency.of(member, 178, measuredAt, "서울시 강남구");

        given(heartRateEmergencyRepository
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(eq(memberId), any()))
                .willReturn(Optional.of(emergency));

        // when
        RecentEmergencyResponse response = heartRateService.getRecentEmergency(memberId);

        // then
        assertThat(response.detected()).isTrue();
        assertThat(response.emergency().heartRate()).isEqualTo(178);
        assertThat(response.emergency().measuredAt()).isEqualTo(measuredAt);
        assertThat(response.emergency().location()).isEqualTo("서울시 강남구");
    }

    @Test
    @DisplayName("위험 사이클 만료 시각은 마지막 감지 시각의 5분 뒤로 연장된다")
    void 위험사이클_만료시각은_마지막_감지시각의_5분뒤다() {
        // given
        Long memberId = 1L;
        LocalDateTime lastDetectedAt = LocalDateTime.now().minusMinutes(2);
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        HeartRateEmergency emergency = HeartRateEmergency.of(member, 180, lastDetectedAt, "서울시 강남구");

        given(heartRateEmergencyRepository
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(eq(memberId), any()))
                .willReturn(Optional.of(emergency));

        // when
        RecentEmergencyResponse response = heartRateService.getRecentEmergency(memberId);

        // then
        assertThat(response.cycleExpiresAt()).isEqualTo(lastDetectedAt.plusMinutes(5));
        assertThat(response.cycleExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("위험 사이클이 종료되었으면 감지되지 않음을 반환한다")
    void 위험사이클이_종료되었으면_감지되지_않음을_반환한다() {
        // given
        Long memberId = 1L;
        given(heartRateEmergencyRepository
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(eq(memberId), any()))
                .willReturn(Optional.empty());

        // when
        RecentEmergencyResponse response = heartRateService.getRecentEmergency(memberId);

        // then
        assertThat(response.detected()).isFalse();
        assertThat(response.emergency()).isNull();
        assertThat(response.cycleExpiresAt()).isNull();
    }

    @Test
    @DisplayName("위험 사이클 조회는 5분 전을 기준 시각으로 사용한다")
    void 위험사이클_조회는_5분전을_기준시각으로_사용한다() {
        // given
        Long memberId = 1L;
        given(heartRateEmergencyRepository
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(eq(memberId), any()))
                .willReturn(Optional.empty());

        // when
        heartRateService.getRecentEmergency(memberId);

        // then
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(heartRateEmergencyRepository).should()
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(eq(memberId), sinceCaptor.capture());
        assertThat(sinceCaptor.getValue()).isBetween(
                LocalDateTime.now().minusMinutes(6), LocalDateTime.now().minusMinutes(4));
    }

    // TEST-012: 심박 수집 배치 멱등성 검증

    @Test
    @DisplayName("동일 배치(배치 시작 시각 일치)를 재전송하면 저장 없이 기존 상태를 반환한다")
    void 동일_배치_재전송시_중복_저장_없이_기존상태를_반환한다() {
        // given
        Long memberId = 1L;
        LocalDateTime batchStart = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(70 + i, batchStart.plusSeconds(i)))
                .toList();
        HeartRateSendRequest duplicateRequest = HeartRateSendRequest.of(measurements, "서울시");

        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        HeartRateResult existingResult = HeartRateResult.of(memberId, HeartRateStatus.NORMAL, 75, batchStart.plusSeconds(14));

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)).willReturn(true);
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.of(existingResult));

        // when
        HeartRateStatusResponse response = heartRateService.processHeartRates(memberId, duplicateRequest);

        // then
        assertThat(response.heartRateStatus()).isEqualTo(HeartRateStatus.NORMAL);
        assertThat(response.heartRate()).isEqualTo(75);
        then(heartRateAnomalyDetector).should(never()).detect(anyLong(), any(), any());
        then(heartRateEventRepository).should(never()).saveAll(any());
        then(heartRateEmergencyRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(HeartRateEmergencyEvent.class));
    }

    @Test
    @DisplayName("신규 배치는 배치 시작 시각이 없으면 정상 처리하고 Event와 Result를 저장한다")
    void 신규_배치는_정상_처리하고_Event와_Result를_저장한다() {
        // given
        Long memberId = 1L;
        LocalDateTime batchStart = LocalDateTime.of(2026, 1, 1, 11, 0, 0);
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(70 + i, batchStart.plusSeconds(i)))
                .toList();
        HeartRateSendRequest newRequest = HeartRateSendRequest.of(measurements, "서울시");
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)).willReturn(false);
        given(heartRateAnomalyDetector.detect(memberId, measurements, "UNKNOWN"))
                .willReturn(new DetectionResult(HeartRateStatus.NORMAL, false));
        HeartRateResult savedResult = HeartRateResult.of(memberId, HeartRateStatus.NORMAL, 84, batchStart.plusSeconds(14));
        given(heartRatePersistenceService.saveAnalysis(memberId, newRequest, HeartRateStatus.NORMAL, false))
                .willReturn(savedResult);

        // when
        HeartRateStatusResponse response = heartRateService.processHeartRates(memberId, newRequest);

        // then
        assertThat(response.heartRateStatus()).isEqualTo(HeartRateStatus.NORMAL);
        assertThat(response.heartRate()).isEqualTo(84);
        then(heartRateAnomalyDetector).should().detect(memberId, measurements, "UNKNOWN");
        then(heartRatePersistenceService).should()
                .saveAnalysis(memberId, newRequest, HeartRateStatus.NORMAL, false);
        then(eventPublisher).should(never()).publishEvent(any(HeartRateEmergencyEvent.class));
    }

    @Test
    @DisplayName("신규 긴급 배치를 저장하면 심박 긴급 이벤트를 발행한다")
    void 신규_긴급_배치를_저장하면_심박_긴급_이벤트를_발행한다() {
        // given
        Long memberId = 1L;
        LocalDateTime batchStart = LocalDateTime.of(2026, 1, 1, 11, 0, 0);
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(160, batchStart.plusSeconds(i)))
                .toList();
        HeartRateSendRequest request = HeartRateSendRequest.of(measurements, "서울시");
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        HeartRateResult savedResult = HeartRateResult.of(
                memberId, HeartRateStatus.EMERGENCY, 160, batchStart.plusSeconds(14));

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)).willReturn(false);
        given(heartRateAnomalyDetector.detect(memberId, measurements, "UNKNOWN"))
                .willReturn(new DetectionResult(HeartRateStatus.EMERGENCY, true));
        given(heartRatePersistenceService.saveAnalysis(memberId, request, HeartRateStatus.EMERGENCY, true))
                .willReturn(savedResult);

        // when
        heartRateService.processHeartRates(memberId, request);

        // then
        then(eventPublisher).should().publishEvent(new HeartRateEmergencyEvent(memberId));
    }

    @Test
    @DisplayName("신규 주의 배치를 저장해도 보호자 알림을 요청하지 않는다")
    void 신규_주의_배치를_저장해도_보호자_알림을_요청하지_않는다() {
        // given
        Long memberId = 1L;
        LocalDateTime batchStart = LocalDateTime.of(2026, 1, 1, 11, 30, 0);
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(110, batchStart.plusSeconds(i)))
                .toList();
        HeartRateSendRequest request = HeartRateSendRequest.of(measurements, "서울시");
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        HeartRateResult savedResult = HeartRateResult.of(
                memberId, HeartRateStatus.CAUTION, 110, batchStart.plusSeconds(14));

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)).willReturn(false);
        given(heartRateAnomalyDetector.detect(memberId, measurements, "UNKNOWN"))
                .willReturn(new DetectionResult(HeartRateStatus.CAUTION, false));
        given(heartRatePersistenceService.saveAnalysis(memberId, request, HeartRateStatus.CAUTION, false))
                .willReturn(savedResult);

        // when
        heartRateService.processHeartRates(memberId, request);

        // then
        then(eventPublisher).should(never()).publishEvent(any(HeartRateEmergencyEvent.class));
    }

    @Test
    @DisplayName("AI 판정이 실패하면 원본 심박 기록을 저장하지 않는다")
    void AI_판정이_실패하면_원본_심박기록을_저장하지_않는다() {
        // given
        Long memberId = 1L;
        LocalDateTime batchStart = LocalDateTime.of(2026, 1, 1, 13, 0, 0);
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(70 + i, batchStart.plusSeconds(i)))
                .toList();
        HeartRateSendRequest newRequest = HeartRateSendRequest.of(measurements, "서울시");
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)).willReturn(false);
        given(heartRateAnomalyDetector.detect(memberId, measurements, "UNKNOWN"))
                .willThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "AI 서버와의 통신에 실패했습니다."));

        // when & then
        assertThatThrownBy(() -> heartRateService.processHeartRates(memberId, newRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR);
        then(heartRatePersistenceService).should(never())
                .saveAnalysis(anyLong(), any(), any(HeartRateStatus.class), anyBoolean());
    }

    @Test
    @DisplayName("신규 이상 배치는 Emergency를 저장하지만 중복 배치에서는 Emergency를 저장하지 않는다")
    void 중복_이상_배치에서_Emergency를_저장하지_않는다() {
        // given
        Long memberId = 1L;
        LocalDateTime batchStart = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(70 + i, batchStart.plusSeconds(i)))
                .toList();
        HeartRateSendRequest duplicateRequest = HeartRateSendRequest.of(measurements, "서울시");
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        HeartRateResult existingResult = HeartRateResult.of(memberId, HeartRateStatus.ANOMALY, 84, batchStart.plusSeconds(14));

        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        given(heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)).willReturn(true);
        given(heartRateResultRepository.findByMemberId(memberId)).willReturn(Optional.of(existingResult));

        // when
        HeartRateStatusResponse response = heartRateService.processHeartRates(memberId, duplicateRequest);

        // then
        assertThat(response.heartRateStatus()).isEqualTo(HeartRateStatus.ANOMALY);
        then(heartRateEmergencyRepository).should(never()).save(any());
    }

    private HeartRateSendRequest request() {
        List<HeartRateMeasurement> measurements = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new HeartRateMeasurement(70 + i, LocalDateTime.now().plusSeconds(i)))
                .toList();
        return HeartRateSendRequest.of(measurements, "서울시");
    }

    private HeartRateEvent heartRateEvent(Integer heartRate, LocalDateTime measuredAt, HeartRateStatus status) {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        return HeartRateEvent.of(member, heartRate, measuredAt, status);
    }

    private HeartRateEmergency heartRateEmergency(Integer heartRate, LocalDateTime measuredAt) {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        return HeartRateEmergency.of(member, heartRate, measuredAt, "서울시 강남구");
    }
}
