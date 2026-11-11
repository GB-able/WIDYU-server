package com.widyu.retention.repository;

import static com.widyu.retention.repository.RetentionFixture.accessLog;
import static com.widyu.retention.repository.RetentionFixture.activeParticipation;
import static com.widyu.retention.repository.RetentionFixture.deletionProcessedParticipation;
import static com.widyu.retention.repository.RetentionFixture.heartRate;
import static com.widyu.retention.repository.RetentionFixture.locationFix;
import static com.widyu.retention.repository.RetentionFixture.notification;
import static com.widyu.retention.repository.RetentionFixture.senior;
import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.member.Member;
import com.widyu.retention.application.RetentionTarget;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, RetentionPurgeRepository.class})
@DisplayName("보관 기간 경과 건수 조회 테스트")
class RetentionPurgeRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 3, 30);

    @Autowired private RetentionPurgeRepository retentionPurgeRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private JPAQueryFactory jpaQueryFactory;

    @Test
    @DisplayName("모든 대상 테이블의 조회 문장이 빈 테이블에서 0건을 반환한다")
    void 모든_대상_테이블의_조회_문장이_빈_테이블에서_0건을_반환한다() {
        for (RetentionTarget target : RetentionTarget.values()) {
            // given
            LocalDateTime cutoff = target.cutoff(NOW);

            // when
            long bound = retentionPurgeRepository.findBoundary(target, cutoff);
            long targets = retentionPurgeRepository.countTargets(target, bound, cutoff, NOW);
            long exempt = retentionPurgeRepository.countExempt(target, bound, cutoff, NOW);

            // then
            assertThat(targets).as(target.table()).isZero();
            assertThat(exempt).as(target.table()).isZero();
        }
    }

    @Test
    @DisplayName("기준 시각이 컷오프보다 앞선 행만 세고 컷오프와 같은 행은 세지 않는다")
    void 기준_시각이_컷오프보다_앞선_행만_세고_컷오프와_같은_행은_세지_않는다() {
        // given
        RetentionTarget target = RetentionTarget.HEART_RATE_EVENT;
        LocalDateTime cutoff = target.cutoff(NOW);
        Member member = entityManager.persist(senior("01011110001"));
        persistHeartRate(member, cutoff.minusDays(3));
        persistHeartRate(member, cutoff.minusSeconds(1));
        persistHeartRate(member, cutoff);
        persistHeartRate(member, cutoff.plusDays(1));

        // when
        long count = countTargets(target);

        // then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제 처리를 마치지 않은 실증 참가자의 행은 대상에서 빠지고 제외 건수로 센다")
    void 삭제_처리를_마치지_않은_실증_참가자의_행은_대상에서_빠지고_제외_건수로_센다() {
        // given
        RetentionTarget target = RetentionTarget.HEART_RATE_EVENT;
        LocalDateTime expired = target.cutoff(NOW).minusDays(1);
        Member participant = entityManager.persist(senior("01011110002"));
        Member processed = entityManager.persist(senior("01011110003"));
        Member ordinary = entityManager.persist(senior("01011110004"));
        entityManager.persist(activeParticipation(participant, "part-0001"));
        entityManager.persist(deletionProcessedParticipation(processed, "part-0002"));
        persistHeartRate(participant, expired);
        persistHeartRate(processed, expired.plusSeconds(1));
        persistHeartRate(ordinary, expired.plusSeconds(2));

        // when
        LocalDateTime cutoff = target.cutoff(NOW);
        long bound = retentionPurgeRepository.findBoundary(target, cutoff);
        long targets = retentionPurgeRepository.countTargets(target, bound, cutoff, NOW);
        long exempt = retentionPurgeRepository.countExempt(target, bound, cutoff, NOW);

        // then
        assertThat(targets).isEqualTo(2);
        assertThat(exempt).isEqualTo(1);
    }

    @Test
    @DisplayName("위급 사유로 보낸 위치는 기간이 지나도 세지 않는다")
    void 위급_사유로_보낸_위치는_기간이_지나도_세지_않는다() {
        // given
        RetentionTarget target = RetentionTarget.LOCATION_FIX;
        LocalDateTime expired = target.cutoff(NOW).minusDays(1);
        persistWithCreatedAt(locationFix(10L, 1, "move"), "location_fix", "location_fix_id", expired);
        persistWithCreatedAt(locationFix(10L, 2, "incident"), "location_fix", "location_fix_id", expired);
        persistWithCreatedAt(locationFix(10L, 3, "keepalive"), "location_fix", "location_fix_id", expired);

        // when
        long count = countTargets(target);

        // then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("알림센터 항목은 만료 시각이 지난 행만 세고 만료 시각이 없는 옛 행은 세지 않는다")
    void 알림센터_항목은_만료_시각이_지난_행만_세고_만료_시각이_없는_옛_행은_세지_않는다() {
        // given
        RetentionTarget target = RetentionTarget.FCM_NOTIFICATION;
        LocalDateTime created = NOW.minusDays(100);
        persistWithCreatedAt(notification("evt-1", created.plusDays(90)), "fcm_notification", "id", created);
        persistWithCreatedAt(notification("evt-2", created.plusDays(180)), "fcm_notification", "id", created);
        persistWithCreatedAt(notification("evt-3", null), "fcm_notification", "id", created);

        // when
        long count = countTargets(target);

        // then
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("위치 열람 기록은 열람 시각 기준 1년으로 센다")
    void 위치_열람_기록은_열람_시각_기준_1년으로_센다() {
        // given
        RetentionTarget target = RetentionTarget.LOCATION_ACCESS_LOG;
        entityManager.persist(accessLog(NOW.minusYears(1).minusDays(1)));
        entityManager.persist(accessLog(NOW.minusDays(364)));
        entityManager.flush();

        // when
        long count = countTargets(target);

        // then
        assertThat(count).isEqualTo(1);
        assertThat(target.exemptsParticipants()).isFalse();
    }

    private long countTargets(RetentionTarget target) {
        LocalDateTime cutoff = target.cutoff(NOW);
        long bound = retentionPurgeRepository.findBoundary(target, cutoff);
        return retentionPurgeRepository.countTargets(target, bound, cutoff, NOW);
    }

    private void persistHeartRate(Member member, LocalDateTime createdAt) {
        persistWithCreatedAt(heartRate(member, createdAt), "heart_rate_event", "heart_rate_event_id", createdAt);
    }

    /** 감사 필드는 저장 시각으로 채워지므로 저장 뒤에 서버 생성 시각을 바꾼다. 오래된 행부터 넣어 PK와 시각 순서를 맞춘다. */
    private void persistWithCreatedAt(Object entity, String table, String pkColumn, LocalDateTime createdAt) {
        entityManager.persistAndFlush(entity);
        Object id = entityManager.getId(entity);
        jdbcTemplate.update("UPDATE " + table + " SET created_at = ? WHERE " + pkColumn + " = ?", createdAt, id);
    }
}
