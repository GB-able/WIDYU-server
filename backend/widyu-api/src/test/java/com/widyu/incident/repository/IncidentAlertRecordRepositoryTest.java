package com.widyu.incident.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.decision.DecisionRecord;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.incident.Incident;
import com.widyu.incident.IncidentKind;
import com.widyu.incident.IncidentResponseValue;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.sensor.SensorBatch;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@DisplayName("IncidentAlertRecordRepository 알림 기록 조회 테스트")
class IncidentAlertRecordRepositoryTest {

    private static final long FROM_MS = 1_762_000_000_000L;
    private static final long TO_MS = FROM_MS + 86_400_000L;
    private static final String BATCH_ID = "01j8zhr0000000000000000001";
    private static final List<IncidentKind> ALERT_KINDS =
            List.of(IncidentKind.HR_ANOMALY, IncidentKind.FALL_SUSPECTED);

    @Autowired private IncidentAlertRecordRepository incidentAlertRecordRepository;
    @Autowired private TestEntityManager entityManager;
    @MockBean private JPAQueryFactory jpaQueryFactory;

    @Test
    @DisplayName("회차 있는 알림을 조회하면 판정 기록의 근거와 판정 묶음의 착용 값이 한 줄에 붙는다")
    void 회차_있는_알림을_조회하면_판정_근거와_착용_값이_한_줄에_붙는다() {
        // given
        Member senior = persistSenior();
        entityManager.persist(hrBatch(senior));
        DecisionRecord decision = decision(senior.getId());
        decision.markDelivered("fcm-7", FROM_MS + 2_000L);
        entityManager.persist(decision);
        Incident incident = incident("inc-run", senior.getId(), "run-01", "dec-1", IncidentKind.HR_ANOMALY,
                FROM_MS + 1_000L);
        ReflectionTestUtils.setField(incident, "response", IncidentResponseValue.OK);
        ReflectionTestUtils.setField(incident, "respondedAtMs", FROM_MS + 30_000L);
        entityManager.persistAndFlush(incident);

        // when
        List<IncidentAlertRow> rows = incidentAlertRecordRepository.findAlertRecords(FROM_MS, TO_MS, ALERT_KINDS);

        // then
        assertThat(rows).hasSize(1);
        IncidentAlertRow row = rows.get(0);
        assertThat(row.incidentRef()).isEqualTo("inc-run");
        assertThat(row.hrBpm()).isEqualTo(185);
        assertThat(row.hrAccuracy()).isEqualTo("HIGH");
        assertThat(row.reason()).isEqualTo("서맥");
        assertThat(row.onBody()).isTrue();
        assertThat(row.wearState()).isEqualTo("WORN_VALID");
        assertThat(row.alertDelivered()).isTrue();
        assertThat(row.alertAtMs()).isEqualTo(FROM_MS + 2_000L);
        assertThat(row.response()).isEqualTo(IncidentResponseValue.OK);
        assertThat(row.respondedAtMs()).isEqualTo(FROM_MS + 30_000L);
    }

    @Test
    @DisplayName("회차 없는 알림을 조회하면 사건 값만 있고 판정 근거는 비어 있다")
    void 회차_없는_알림을_조회하면_판정_근거는_비어_있다() {
        // given
        Member senior = persistSenior();
        Incident incident = incident("inc-product", senior.getId(), null, null, IncidentKind.HR_ANOMALY,
                FROM_MS + 1_000L);
        incident.markInitialAlertSent(FROM_MS + 1_000L);
        entityManager.persistAndFlush(incident);

        // when
        List<IncidentAlertRow> rows = incidentAlertRecordRepository.findAlertRecords(FROM_MS, TO_MS, ALERT_KINDS);

        // then
        assertThat(rows).hasSize(1);
        IncidentAlertRow row = rows.get(0);
        assertThat(row.initialAlertSentAtMs()).isEqualTo(FROM_MS + 1_000L);
        assertThat(row.detectionCount()).isEqualTo(1);
        assertThat(row.hrBpm()).isNull();
        assertThat(row.onBody()).isNull();
        assertThat(row.alertDelivered()).isNull();
    }

    @Test
    @DisplayName("기간 밖 알림과 어르신이 직접 누른 도움 요청은 조회하지 않고 알림 시각 순으로 돌려준다")
    void 기간_밖_알림과_도움_요청은_빼고_알림_시각_순으로_돌려준다() {
        // given
        Member senior = persistSenior();
        entityManager.persist(incident("inc-late", senior.getId(), null, null, IncidentKind.FALL_SUSPECTED,
                FROM_MS + 5_000L));
        entityManager.persist(incident("inc-early", senior.getId(), null, null, IncidentKind.HR_ANOMALY,
                FROM_MS));
        entityManager.persist(incident("inc-help", senior.getId(), null, null, IncidentKind.SELF_HELP_REQUEST,
                FROM_MS + 2_000L));
        entityManager.persist(incident("inc-before", senior.getId(), null, null, IncidentKind.HR_ANOMALY,
                FROM_MS - 1L));
        entityManager.persistAndFlush(incident("inc-at-end", senior.getId(), null, null, IncidentKind.HR_ANOMALY,
                TO_MS));

        // when
        List<IncidentAlertRow> rows = incidentAlertRecordRepository.findAlertRecords(FROM_MS, TO_MS, ALERT_KINDS);

        // then
        assertThat(rows).extracting(IncidentAlertRow::incidentRef).containsExactly("inc-early", "inc-late");
    }

    private Member persistSenior() {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        entityManager.persistAndFlush(member);
        return member;
    }

    private Incident incident(String ref, Long memberId, String runId, String decisionId, IncidentKind kind,
                              long openedAtMs) {
        return Incident.builder().incidentRef(ref).memberId(memberId).runId(runId).decisionId(decisionId)
                .kind(kind).level("EMERGENCY").openedAtMs(openedAtMs).respondByMs(openedAtMs + 90_000L).build();
    }

    private DecisionRecord decision(Long memberId) {
        return DecisionRecord.builder()
                .decisionId("dec-1")
                .memberId(memberId)
                .runId("run-01")
                .streamIdsUsed("[\"" + BATCH_ID + "\"]")
                .decisionAtMs(FROM_MS + 500L)
                .decisionOutput("ALERT")
                .deciderId("widyu-ai-hr")
                .deciderVersion("ver7")
                .inputCutoffMs(FROM_MS + 400L)
                .featureSupportEndMs(FROM_MS + 300L)
                .modelAvailableAtServerMaxMs(FROM_MS + 400L)
                .windowStartMs(FROM_MS + 300L)
                .windowEndMs(FROM_MS + 300L)
                .severity("EMERGENCY")
                .triggerBatchId(BATCH_ID)
                .hrBpm(185)
                .hrMeasuredAtMs(FROM_MS + 300L)
                .hrAccuracy("HIGH")
                .reason("서맥")
                .build();
    }

    private SensorBatch hrBatch(Member member) {
        return SensorBatch.builder()
                .batchId(BATCH_ID)
                .member(member)
                .stream("hr")
                .source("watch")
                .deviceId("watch-1")
                .sessionId("s-1")
                .seq(1L)
                .runId("run-01")
                .bootId("b1")
                .clockMappingId("cm-1")
                .anchorElapsedNs(1L)
                .anchorEpochMs(FROM_MS)
                .uncertaintyMs(2.0)
                .sampleCount(3)
                .measuredAtStartMs(FROM_MS)
                .measuredAtEndMs(FROM_MS + 300L)
                .serverReceivedAtMs(FROM_MS + 400L)
                .acceptedAtMs(FROM_MS + 400L)
                .persistedAtMs(FROM_MS + 400L)
                .modelAvailableAtServerMs(FROM_MS + 400L)
                .onBody(true)
                .wearState("WORN_VALID")
                .qualityStatus("OK")
                .gyroBackfill(false)
                .isResend(false)
                .configMismatch(false)
                .s3Key("sensor/hr/1.json")
                .byteSize(256)
                .payloadSha256("b".repeat(64))
                .build();
    }
}
