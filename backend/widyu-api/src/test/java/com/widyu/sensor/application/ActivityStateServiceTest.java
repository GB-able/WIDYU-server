package com.widyu.sensor.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.properties.SensorProperties;
import com.widyu.location.raw.LocationFix;
import com.widyu.location.raw.repository.LocationFixRepository;
import com.widyu.sensor.ActivityWindow;
import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.dto.request.SensorBatchRequest;
import com.widyu.sensor.repository.ActivityWindowRepository;
import com.widyu.sensor.repository.SensorBatchRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 손목 움직임 5초 구간 상태의 계산, 저장, 심박 시각 조회(LLD-0138 7절). 실제 JPA와 H2로 잠금 조회까지 돈다. */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, ActivityStateService.class})
@DisplayName("ActivityStateService 테스트")
class ActivityStateServiceTest {

    // 5초 경계에 맞춘 측정 시각(2025-11-12 03:00:00 KST 근처)
    private static final long T0 = 1_762_884_000_000L;
    private static final long DT_NS = 20_000_000L;
    private static final SensorProperties.Activity ACTIVITY =
            new SensorProperties.Activity(5, 4, 0.04, 0.20, 600, 20, 60, 50);

    @Autowired private ActivityStateService service;
    @Autowired private ActivityWindowRepository windows;
    @Autowired private LocationFixRepository fixes;
    @MockBean private SensorBatchRepository sensorBatchRepository;
    @MockBean private SensorProperties sensorProperties;
    @MockBean private JPAQueryFactory jpaQueryFactory;

    @ParameterizedTest(name = "{1}mg·{2}mg 교대면 {3}")
    @CsvSource({
            "101, 1039, 961, REST",
            "102, 1040, 960, LOW",
            "103, 1199, 801, LOW",
            "104, 1200, 800, ACTIVE"
    })
    @DisplayName("움직임 크기가 경계값과 같으면 위쪽 상태가 된다")
    void 움직임_크기가_경계값과_같으면_위쪽_상태가_된다(long memberId, int high, int low, String expected) {
        // given: 크기가 high, low로 번갈아 오면 표준편차는 (high - low) / 2 mg다
        given(sensorProperties.activity()).willReturn(ACTIVITY);

        // when
        service.record(memberId, "imu_watch", clock(), acc(0, high, low, high, low), null);

        // then
        ActivityWindow window = windows.findForUpdate(memberId, T0).orElseThrow();
        assertThat(window.getState()).isEqualTo(ActivityWindow.State.valueOf(expected));
        assertThat(window.getSampleCount()).isEqualTo(4);
        assertThat(window.getWindowEndMs()).isEqualTo(T0 + 5_000);
    }

    @Test
    @DisplayName("한 구간을 나눠 담은 묶음이 역순으로 와도 구간 합계와 상태가 같다")
    void 한_구간을_나눠_담은_묶음이_역순으로_와도_구간_합계와_상태가_같다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        SensorBatchRequest.Axis first = acc(0, 1040, 960);
        SensorBatchRequest.Axis second = acc(1_000, 1200, 800);

        // when
        service.record(201L, "imu_watch", clock(), first, null);
        service.record(201L, "imu_watch", clock(), second, null);
        service.record(202L, "imu_watch", clock(), second, null);
        service.record(202L, "imu_watch", clock(), first, null);

        // then
        ActivityWindow inOrder = windows.findForUpdate(201L, T0).orElseThrow();
        ActivityWindow reversed = windows.findForUpdate(202L, T0).orElseThrow();
        assertThat(inOrder.getSampleCount()).isEqualTo(4);
        assertThat(reversed.getSampleCount()).isEqualTo(inOrder.getSampleCount());
        assertThat(reversed.getMagnitudeSumMg()).isEqualTo(inOrder.getMagnitudeSumMg());
        assertThat(reversed.getMagnitudeSqSumMg()).isEqualTo(inOrder.getMagnitudeSqSumMg());
        assertThat(reversed.getState()).isEqualTo(inOrder.getState());
        assertThat(windows.findByMemberIdAndWindowStartMsBetween(201L, T0 - 60_000, T0 + 60_000)).hasSize(1);
    }

    @Test
    @DisplayName("구간 경계에 걸친 묶음은 측정 시각에 따라 두 구간으로 나눠 더한다")
    void 구간_경계에_걸친_묶음은_측정_시각에_따라_두_구간으로_나눠_더한다() {
        // given: 4960, 4980, 5000, 5020ms
        given(sensorProperties.activity()).willReturn(ACTIVITY);

        // when
        service.record(301L, "imu_watch", clock(), acc(4_960, 1000, 1000, 1000, 1000), null);

        // then
        assertThat(windows.findForUpdate(301L, T0).orElseThrow().getSampleCount()).isEqualTo(2);
        assertThat(windows.findForUpdate(301L, T0 + 5_000).orElseThrow().getSampleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("원 묶음이 이미 저장된 재전송 묶음은 다시 더하지 않는다")
    void 원_묶음이_이미_저장된_재전송_묶음은_다시_더하지_않는다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        given(sensorBatchRepository.findByBatchId("01j8y000000000000000000000"))
                .willReturn(Optional.of(Mockito.mock(SensorBatch.class)));
        SensorBatchRequest.Axis acc = acc(0, 1040, 960, 1040, 960);
        service.record(401L, "imu_watch", clock(), acc, null);
        SensorBatchRequest.Resend resend = new SensorBatchRequest.Resend(
                true, "01j8y000000000000000000000", 7L, null, "s-1", T0 + 60_000);

        // when
        service.record(401L, "imu_watch", clock(), acc, resend);

        // then
        assertThat(windows.findForUpdate(401L, T0).orElseThrow().getSampleCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("휴대폰 IMU와 가속도 없는 보강 묶음은 구간을 만들지 않는다")
    void 휴대폰_IMU와_가속도_없는_보강_묶음은_구간을_만들지_않는다() {
        // when
        service.record(501L, "imu_phone", clock(), acc(0, 1040, 960, 1040, 960), null);
        service.record(501L, "imu_watch", clock(), null, null);

        // then
        assertThat(windows.findByMemberIdAndWindowStartMsBetween(501L, T0 - 60_000, T0 + 60_000)).isEmpty();
    }

    @Test
    @DisplayName("활동 상태 저장이 실패해도 예외를 던지지 않는다")
    void 활동_상태_저장이_실패해도_예외를_던지지_않는다() {
        // given
        given(sensorProperties.activity()).willThrow(new IllegalStateException("설정 누락"));

        // when & then
        // IMU 묶음 저장 뒤에 부르는 부수 작업이라 실패가 묶음 응답을 바꾸면 안 된다(LLD-0138 6절).
        assertThatCode(() -> service.record(601L, "imu_watch", clock(), acc(0, 1040, 960), null))
                .doesNotThrowAnyException();
        assertThat(windows.findByMemberIdAndWindowStartMsBetween(601L, T0 - 60_000, T0 + 60_000)).isEmpty();
    }

    @Test
    @DisplayName("샘플이 모자란 구간은 상태를 정하지 않고 그 시각에는 UNKNOWN이 붙는다")
    void 샘플이_모자란_구간은_상태를_정하지_않고_UNKNOWN이_붙는다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        service.record(701L, "imu_watch", clock(), acc(0, 1040, 960), null);

        // when
        LongFunction<String> contexts = service.contexts(701L, T0 + 1_000, T0 + 1_000);

        // then
        assertThat(windows.findForUpdate(701L, T0).orElseThrow().getState()).isNull();
        assertThat(contexts.apply(T0 + 1_000)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("심박 시각을 포함하는 구간의 상태를 붙이고 구간이 없으면 UNKNOWN이다")
    void 심박_시각을_포함하는_구간의_상태를_붙이고_구간이_없으면_UNKNOWN이다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        saveWindow(801L, T0, ActivityWindow.State.LOW);

        // when
        LongFunction<String> contexts = service.contexts(801L, T0, T0 + 10_000);

        // then
        assertThat(contexts.apply(T0 + 4_999)).isEqualTo("LOW");
        assertThat(contexts.apply(T0 + 5_000)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("마지막 ACTIVE 구간이 끝나고 600초 안이면 RECOVERY이고 넘으면 원래 상태다")
    void 마지막_ACTIVE_구간이_끝나고_600초_안이면_RECOVERY이고_넘으면_원래_상태다() {
        // given: ACTIVE 구간은 T0+5000에 끝난다
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        saveWindow(901L, T0, ActivityWindow.State.ACTIVE);
        saveWindow(901L, T0 + 605_000, ActivityWindow.State.REST);
        saveWindow(901L, T0 + 610_000, ActivityWindow.State.REST);

        // when
        LongFunction<String> contexts = service.contexts(901L, T0, T0 + 615_000);

        // then
        assertThat(contexts.apply(T0 + 1_000)).isEqualTo("ACTIVE");
        assertThat(contexts.apply(T0 + 605_000)).isEqualTo("RECOVERY");
        assertThat(contexts.apply(T0 + 610_000)).isEqualTo("REST");
    }

    @Test
    @DisplayName("60초 전과 그 뒤 위치가 모두 시속 20km를 넘으면 움직임 상태보다 IN_VEHICLE이 앞선다")
    void 육십초_전과_그_뒤_위치가_모두_빠르면_IN_VEHICLE이_앞선다() {
        // given: 10m/s = 시속 36km
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        long ts = T0 + 200_000;
        saveWindow(1001L, windowOf(ts), ActivityWindow.State.REST);
        saveFix(1001L, 1, ts - 70_000, 10.0, 10.0);
        saveFix(1001L, 2, ts - 10_000, 10.0, 10.0);

        // when & then
        assertThat(service.contexts(1001L, ts, ts).apply(ts)).isEqualTo("IN_VEHICLE");
    }

    @Test
    @DisplayName("사이에 느린 위치가 있거나 60초 전 위치가 없거나 정확도가 나쁘면 IN_VEHICLE이 아니다")
    void 느린_위치나_앞쪽_위치_없음이나_나쁜_정확도면_IN_VEHICLE이_아니다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        long ts = T0 + 200_000;
        // 느린 위치(3m/s = 시속 10.8km)가 끼어 있다
        saveWindow(1101L, windowOf(ts), ActivityWindow.State.REST);
        saveFix(1101L, 1, ts - 70_000, 10.0, 10.0);
        saveFix(1101L, 2, ts - 30_000, 3.0, 10.0);
        saveFix(1101L, 3, ts - 10_000, 10.0, 10.0);
        // 60초 전 위치가 없다
        saveWindow(1102L, windowOf(ts), ActivityWindow.State.REST);
        saveFix(1102L, 1, ts - 10_000, 10.0, 10.0);
        // 60초 전 위치의 정확도가 50m보다 나쁘다
        saveWindow(1103L, windowOf(ts), ActivityWindow.State.REST);
        saveFix(1103L, 1, ts - 70_000, 10.0, 80.0);
        saveFix(1103L, 2, ts - 10_000, 10.0, 10.0);

        // when & then
        assertThat(service.contexts(1101L, ts, ts).apply(ts)).isEqualTo("REST");
        assertThat(service.contexts(1102L, ts, ts).apply(ts)).isEqualTo("REST");
        assertThat(service.contexts(1103L, ts, ts).apply(ts)).isEqualTo("REST");
    }

    private SensorBatchRequest.Clock clock() {
        return SensorBatchRequest.Clock.of("b1", "cm-1", "0", T0, 1.0);
    }

    /** offsetMs부터 20ms 간격으로 z축 크기만 있는 샘플. 벡터 크기가 곧 그 값이다. */
    private SensorBatchRequest.Axis acc(long offsetMs, int... magnitudesMg) {
        List<List<Integer>> mg = new ArrayList<>();
        for (int magnitude : magnitudesMg) {
            mg.add(List.of(0, 0, magnitude));
        }
        List<Long> dtNs = new ArrayList<>(Collections.nCopies(magnitudesMg.length - 1, DT_NS));
        return new SensorBatchRequest.Axis(
                50.0, magnitudesMg.length, String.valueOf(offsetMs * 1_000_000L), dtNs, mg, null);
    }

    private void saveWindow(Long memberId, long startMs, ActivityWindow.State state) {
        ActivityWindow window = ActivityWindow.empty(memberId, startMs, startMs + 5_000);
        window.add(250, 0L, 0L, state);
        windows.save(window);
    }

    private long windowOf(long tsMs) {
        return Math.floorDiv(tsMs, 5_000L) * 5_000L;
    }

    private void saveFix(Long memberId, long seq, long tsMs, double speedMps, double accuracyM) {
        fixes.save(LocationFix.builder()
                .memberId(memberId).deviceId("ph-1").sessionId("s-" + memberId).seq(seq)
                .tsMs(tsMs).lat(37.5).lon(127.0).accuracyM(accuracyM).speedMps(speedMps)
                .isMock(false).reason("move")
                .serverReceivedAtMs(tsMs).acceptedAtMs(tsMs).persistedAtMs(tsMs)
                .payload("{}").payloadSha256("0".repeat(64))
                .build());
    }
}
