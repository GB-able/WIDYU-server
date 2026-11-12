package com.widyu.sensor.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.properties.SensorProperties;
import com.widyu.location.raw.LocationFix;
import com.widyu.location.raw.repository.LocationFixRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.sensor.ActivityWindow;
import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.dto.request.SensorBatchRequest;
import com.widyu.sensor.repository.ActivityWindowRepository;
import com.widyu.sensor.repository.SensorBatchRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

/** 손목 움직임 5초 구간 상태의 계산, 저장, 심박 시각 조회(LLD-0138 7절). 실제 JPA와 H2로 잠금 조회까지 돈다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@DisplayName("ActivityStateService 테스트")
class ActivityStateServiceTest {

    // 5초 경계에 맞춘 측정 시각(2025-11-12 03:00:00 KST 근처)
    private static final long T0 = 1_762_884_000_000L;
    private static final long DT_NS = 20_000_000L;
    private static final String BATCH = "01j8y111111111111111111111";
    private static final String ORIGINAL = "01j8y000000000000000000000";
    private static final String RESEND_1 = "01j8y000000000000000000001";
    private static final String RESEND_2 = "01j8y000000000000000000002";
    private static final SensorProperties.Activity ACTIVITY =
            new SensorProperties.Activity(5, 4, 0.04, 0.20, 600, 20, 60, 50);

    @Autowired private ActivityWindowRepository windows;
    @Autowired private LocationFixRepository fixes;
    @Autowired private SensorBatchRepository sensorBatches;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TestEntityManager entityManager;
    // 다른 리포지토리 테스트와 같은 컨텍스트를 쓰도록 서비스는 빈으로 등록하지 않고 직접 만든다(CI 테스트 힙 1g)
    @MockBean private JPAQueryFactory jpaQueryFactory;
    private final SensorProperties sensorProperties = mock(SensorProperties.class);
    private ActivityStateService service;

    @BeforeEach
    void setUp() {
        service = new ActivityStateService(windows, sensorBatches, fixes, sensorProperties, transactionManager);
    }

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
        service.record(memberId, BATCH, "imu_watch", clock(), acc(0, high, low, high, low), null);

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
        service.record(201L, BATCH, "imu_watch", clock(), first, null);
        service.record(201L, BATCH, "imu_watch", clock(), second, null);
        service.record(202L, BATCH, "imu_watch", clock(), second, null);
        service.record(202L, BATCH, "imu_watch", clock(), first, null);

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
        service.record(301L, BATCH, "imu_watch", clock(), acc(4_960, 1000, 1000, 1000, 1000), null);

        // then
        assertThat(windows.findForUpdate(301L, T0).orElseThrow().getSampleCount()).isEqualTo(2);
        assertThat(windows.findForUpdate(301L, T0 + 5_000).orElseThrow().getSampleCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("원 묶음이 먼저 저장된 뒤 온 재전송 묶음은 다시 더하지 않는다")
    void 원_묶음이_먼저_저장된_뒤_온_재전송_묶음은_다시_더하지_않는다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        Member member = persistMember();
        SensorBatchRequest.Axis acc = acc(0, 1040, 960, 1040, 960);
        persistBatch(member, ORIGINAL, null);
        service.record(member.getId(), ORIGINAL, "imu_watch", clock(), acc, null);
        persistBatch(member, RESEND_1, ORIGINAL);

        // when
        service.record(member.getId(), RESEND_1, "imu_watch", clock(), acc, resendOf(ORIGINAL));

        // then
        assertThat(windows.findForUpdate(member.getId(), T0).orElseThrow().getSampleCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("재전송 묶음이 먼저 저장된 뒤 온 원 묶음은 다시 더하지 않는다")
    void 재전송_묶음이_먼저_저장된_뒤_온_원_묶음은_다시_더하지_않는다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        Member member = persistMember();
        SensorBatchRequest.Axis acc = acc(0, 1040, 960, 1040, 960);
        persistBatch(member, RESEND_1, ORIGINAL);
        service.record(member.getId(), RESEND_1, "imu_watch", clock(), acc, resendOf(ORIGINAL));
        persistBatch(member, ORIGINAL, null);

        // when
        service.record(member.getId(), ORIGINAL, "imu_watch", clock(), acc, null);

        // then
        assertThat(windows.findForUpdate(member.getId(), T0).orElseThrow().getSampleCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("같은 원 묶음의 재전송이 둘이면 먼저 저장된 하나만 더한다")
    void 같은_원_묶음의_재전송이_둘이면_먼저_저장된_하나만_더한다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        Member member = persistMember();
        SensorBatchRequest.Axis acc = acc(0, 1040, 960, 1040, 960);
        // 두 묶음이 거의 동시에 저장돼 둘 다 저장된 뒤에 계산이 돌아도 결과가 같다.
        persistBatch(member, RESEND_1, ORIGINAL);
        persistBatch(member, RESEND_2, ORIGINAL);

        // when
        service.record(member.getId(), RESEND_2, "imu_watch", clock(), acc, resendOf(ORIGINAL));
        service.record(member.getId(), RESEND_1, "imu_watch", clock(), acc, resendOf(ORIGINAL));

        // then
        assertThat(windows.findForUpdate(member.getId(), T0).orElseThrow().getSampleCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("원 묶음이 다른 회원 것이거나 자기 자신이면 재전송 묶음도 더한다")
    void 원_묶음이_다른_회원_것이거나_자기_자신이면_재전송_묶음도_더한다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        Member other = persistMember();
        Member member = persistMember();
        Member self = persistMember();
        SensorBatchRequest.Axis acc = acc(0, 1040, 960, 1040, 960);
        persistBatch(other, ORIGINAL, null);
        persistBatch(member, RESEND_1, ORIGINAL);
        persistBatch(self, RESEND_2, RESEND_2);

        // when
        service.record(member.getId(), RESEND_1, "imu_watch", clock(), acc, resendOf(ORIGINAL));
        service.record(self.getId(), RESEND_2, "imu_watch", clock(), acc, resendOf(RESEND_2));

        // then
        assertThat(windows.findForUpdate(member.getId(), T0).orElseThrow().getSampleCount()).isEqualTo(4);
        assertThat(windows.findForUpdate(self.getId(), T0).orElseThrow().getSampleCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("축 값의 제곱합이 long 범위를 넘어도 큰 움직임은 ACTIVE로 저장된다")
    void 축_값의_제곱합이_long_범위를_넘어도_큰_움직임은_ACTIVE로_저장된다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        List<Integer> huge = List.of(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        List<Integer> zero = List.of(0, 0, 0);
        SensorBatchRequest.Axis acc = new SensorBatchRequest.Axis(
                50.0, 4, "0", List.of(DT_NS, DT_NS, DT_NS), List.of(huge, zero, huge, zero), null);

        // when
        service.record(404L, BATCH, "imu_watch", clock(), acc, null);

        // then
        assertThat(windows.findForUpdate(404L, T0).orElseThrow().getState()).isEqualTo(ActivityWindow.State.ACTIVE);
    }

    @Test
    @DisplayName("휴대폰 IMU와 가속도 없는 보강 묶음은 구간을 만들지 않는다")
    void 휴대폰_IMU와_가속도_없는_보강_묶음은_구간을_만들지_않는다() {
        // when
        service.record(501L, BATCH, "imu_phone", clock(), acc(0, 1040, 960, 1040, 960), null);
        service.record(501L, BATCH, "imu_watch", clock(), null, null);

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
        assertThatCode(() -> service.record(601L, BATCH, "imu_watch", clock(), acc(0, 1040, 960), null))
                .doesNotThrowAnyException();
        assertThat(windows.findByMemberIdAndWindowStartMsBetween(601L, T0 - 60_000, T0 + 60_000)).isEmpty();
    }

    @Test
    @DisplayName("샘플이 모자란 구간은 상태를 정하지 않고 그 시각에는 UNKNOWN이 붙는다")
    void 샘플이_모자란_구간은_상태를_정하지_않고_UNKNOWN이_붙는다() {
        // given
        given(sensorProperties.activity()).willReturn(ACTIVITY);
        service.record(701L, BATCH, "imu_watch", clock(), acc(0, 1040, 960), null);

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

    private Member persistMember() {
        return entityManager.persistAndFlush(Member.createMember(MemberType.SENIOR, "시니어", "01012345678"));
    }

    private SensorBatchRequest.Resend resendOf(String originalBatchId) {
        return new SensorBatchRequest.Resend(true, originalBatchId, 7L, null, "s-1", T0 + 60_000);
    }

    /** 묶음은 활동 계산 전에 저장된다. 원 묶음이 없으면 재전송이 아니다. */
    private void persistBatch(Member member, String batchId, String originalBatchId) {
        entityManager.persistAndFlush(SensorBatch.builder()
                .batchId(batchId).member(member).stream("imu_watch").source("watch")
                .deviceId("watch-1").sessionId("s-1").seq(1L).bootId("b1").clockMappingId("cm-1")
                .anchorElapsedNs(0L).anchorEpochMs(T0).uncertaintyMs(1.0)
                .measuredAtStartMs(T0).measuredAtEndMs(T0 + 60)
                .serverReceivedAtMs(T0).acceptedAtMs(T0).persistedAtMs(T0).modelAvailableAtServerMs(T0)
                .onBody(true).qualityStatus("OK").gyroBackfill(false).isResend(originalBatchId != null).originalBatchId(originalBatchId)
                .configMismatch(false).s3Key("sensor/" + batchId + ".json").byteSize(256)
                .payloadSha256("b".repeat(64))
                .build());
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
