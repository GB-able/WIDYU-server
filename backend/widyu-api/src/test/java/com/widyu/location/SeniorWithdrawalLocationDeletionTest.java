package com.widyu.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.repository.LocationAccessLogRepository;
import com.widyu.location.raw.LocationFix;
import com.widyu.location.raw.repository.LocationFixRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.study.StudyParticipation;
import com.widyu.study.WithdrawalScope;
import com.widyu.study.repository.StudyParticipationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@DisplayName("어르신 탈퇴 위치 기록 삭제 쿼리 테스트")
class SeniorWithdrawalLocationDeletionTest {

    private static final LocalDateTime ACCESSED_AT = LocalDateTime.of(2026, 11, 20, 9, 0);

    @Autowired private LocationAccessLogRepository locationAccessLogRepository;
    @Autowired private LocationFixRepository locationFixRepository;
    @Autowired private StudyParticipationRepository studyParticipationRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("탈퇴한 어르신의 열람 기록과 위치 원본을 지우면 다른 어르신의 기록은 남는다")
    void 탈퇴한_어르신의_위치_기록만_지운다() {
        // given
        entityManager.persist(LocationAccessLog.of(1L, 7L, LocationAccessPath.REST_LAST, ACCESSED_AT));
        entityManager.persist(LocationAccessLog.of(1L, 7L, LocationAccessPath.WS_SUBSCRIBE, ACCESSED_AT));
        entityManager.persist(LocationAccessLog.of(1L, 8L, LocationAccessPath.REST_LAST, ACCESSED_AT));
        entityManager.persist(locationFix(7L, 1L));
        entityManager.persist(locationFix(7L, 2L));
        entityManager.persist(locationFix(8L, 3L));
        entityManager.flush();

        // when
        int accessLogs = locationAccessLogRepository.deleteBySeniorMemberId(7L);
        int fixes = locationFixRepository.deleteByMemberId(7L);

        // then
        assertThat(accessLogs).isEqualTo(2);
        assertThat(fixes).isEqualTo(2);
        assertThat(locationAccessLogRepository.findAll())
                .extracting(LocationAccessLog::getSeniorMemberId).containsExactly(8L);
        assertThat(locationFixRepository.findAll()).extracting(LocationFix::getMemberId).containsExactly(8L);
    }

    @Test
    @DisplayName("연구 자료 삭제를 마치지 않은 참여만 실증 참가자로 본다")
    void 연구_자료_삭제를_마치지_않은_참여만_실증_참가자로_본다() {
        // given
        Member active = entityManager.persist(Member.createMember(MemberType.SENIOR, "시니어", "01011112222"));
        Member processed = entityManager.persist(Member.createMember(MemberType.SENIOR, "시니어", "01011113333"));
        entityManager.persist(participation(active, "part-active"));
        StudyParticipation done = participation(processed, "part-done");
        done.withdraw(WithdrawalScope.ALL, null, LocalDateTime.of(2026, 10, 1, 0, 0));
        done.markDeletionProcessed(LocalDateTime.of(2026, 10, 2, 0, 0));
        entityManager.persist(done);
        entityManager.flush();

        // when & then
        assertThat(studyParticipationRepository.existsByMemberIdAndDeletionProcessedAtIsNull(active.getId())).isTrue();
        assertThat(studyParticipationRepository.existsByMemberIdAndDeletionProcessedAtIsNull(processed.getId()))
                .isFalse();
    }

    private static StudyParticipation participation(Member member, String participationId) {
        return StudyParticipation.of("STUDY-2026", participationId, member, "IRB-v1",
                LocalDate.of(2026, 9, 20), Map.of(), null, null, null, null);
    }

    private static LocationFix locationFix(Long memberId, long seq) {
        return LocationFix.builder()
                .memberId(memberId).deviceId("ph-9c1").sessionId("s-2026").seq(seq)
                .tsMs(1_760_000_000_000L + seq).lat(37.55).lon(126.97).isMock(false).reason("periodic")
                .serverReceivedAtMs(1L).acceptedAtMs(1L).persistedAtMs(1L)
                .payload("{}").payloadSha256("0".repeat(64))
                .build();
    }
}
