package com.widyu.goal.walk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.walk.application.WalkService;
import com.widyu.goal.walk.dto.request.UpdateStepsRequest;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.walk.Walk;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 같은 날 걸음 수 동기화가 겹칠 때 작은 값이 큰 값을 덮지 않는지 실제 MySQL에서 확인한다(#950).
 *
 * <p>겹침은 타이밍이 아니라 잠금으로 만든다. 먼저 들어온 동기화 역할의 연결이 걸음 기록 행을 {@code FOR UPDATE}로
 * 잡은 채 기다리면, 검사 대상 동기화(3,000보)는 그 잠금을 기다린다. 그 대기를
 * {@code performance_schema.data_lock_waits}로 확인한 뒤 상대가 9,000보를 쓰고 커밋한다. 잠금 없이 읽던 이전
 * 방식은 0보를 읽고 3,000보로 덮어써 9,000보가 사라졌다.
 *
 * <p>실행: {@code WALK_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/walk_race_<name> DB_USERNAME=root DB_PASSWORD=
 * ./gradlew :backend:widyu-api:test --tests 'com.widyu.goal.walk.integration.WalkStepsConcurrentSyncMySqlTest'}
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create",
        "logging.level.org.hibernate.SQL=warn",
        "logging.level.org.hibernate.type=warn",
        "logging.level.org.hibernate.orm.jdbc.bind=warn",
        "firebase.config-path=/dev/null",
        "s3.credentials.access-key=test",
        "s3.credentials.secret-key=test",
        "s3.region.statics=ap-northeast-2",
        "s3.bucket-name=test-bucket",
        "coolsms.api-key=test",
        "coolsms.api-secret=test",
        "coolsms.api-url=https://api.coolsms.co.kr",
        "coolsms.from-phone-number=01000000000",
        "coolsms.verification-code-length=6",
        "coolsms.verification-code-ttl=300",
        "coolsms.message-template=인증번호: {code}"
})
@EnabledIfEnvironmentVariable(named = "WALK_MYSQL_URL", matches = ".+")
@Tag("walk-mysql")
@DisplayName("걸음 수 동시 동기화 MySQL 재현")
class WalkStepsConcurrentSyncMySqlTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;
    @MockBean private MemberUtil memberUtil;

    @Autowired private WalkService walkService;
    @Autowired private WalkRepository walkRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("WALK_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/walk_race_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("루프백의 일회용 walk_race_ 데이터베이스만 허용합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> envOr("DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> envOr("DB_PASSWORD", ""));
    }

    @Test
    @DisplayName("먼저 들어온 동기화가 커밋 전에 9000보를 쓰는 동안 3000보 동기화가 겹치면 9000보가 남고 포인트는 한 번 지급된다")
    void 동기화가_겹쳐도_작은_값이_큰_값을_덮지_않는다() throws Exception {
        // given
        Long memberId = saveSenior();
        Long walkId = saveWalk(memberId, 5000);
        given(memberUtil.getCurrentMember())
                .willAnswer(invocation -> memberRepository.findById(memberId).orElseThrow());

        // when
        Throwable failure = syncWhileEarlierSyncCommits(walkId, 9000, 3000);

        // then
        assertThat(failure).isNull();
        Map<String, Object> walk = jdbc.queryForMap(
                "SELECT actual_steps, rewarded FROM walk WHERE id = ?", walkId);
        assertThat(((Number) walk.get("actual_steps")).intValue()).isEqualTo(9000);
        assertThat((Boolean) walk.get("rewarded")).isTrue();
        Integer rewards = jdbc.queryForObject(
                "SELECT COUNT(*) FROM point_history WHERE operation_key = ?", Integer.class, "WALK_REWARD:" + walkId);
        assertThat(rewards).isEqualTo(1);
    }

    /** 먼저 들어온 동기화가 행을 잡은 채 검사 대상을 돌리고, 검사 대상이 잠금을 기다리면 큰 값을 쓰고 커밋한다. */
    private Throwable syncWhileEarlierSyncCommits(Long walkId, int earlierSteps, int laterSteps) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection earlier = dataSource.getConnection()) {
            earlier.setAutoCommit(false);
            try (PreparedStatement statement = earlier.prepareStatement(
                    "SELECT actual_steps FROM walk WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, walkId);
                statement.executeQuery();
            }

            Future<Throwable> result = executor.submit(() -> {
                try {
                    walkService.updateSteps(new UpdateStepsRequest(laterSteps, LocalDate.now()));
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });
            awaitLockWait(result);
            try (PreparedStatement statement = earlier.prepareStatement(
                    "UPDATE walk SET actual_steps = ? WHERE id = ?")) {
                statement.setInt(1, earlierSteps);
                statement.setLong(2, walkId);
                statement.executeUpdate();
            }
            earlier.commit();
            return result.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 검사 대상 동기화가 상대의 행 잠금을 기다리기 시작할 때까지 기다린다. 그 전에 끝나면 결과를 담아 실패시킨다. */
    private void awaitLockWait(Future<Throwable> later) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (later.isDone()) {
                throw new IllegalStateException("검사 대상 동기화가 잠금 대기 전에 끝났습니다.", later.get());
            }
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("검사 대상 동기화가 행 잠금 대기에 들어가지 않았습니다.");
    }

    private Long saveSenior() {
        int seq = SEQ.incrementAndGet();
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member senior = memberRepository.save(Member.createMember(MemberType.SENIOR, "시니어" + seq,
                    "010" + String.format("%08d", seq)));
            Family family = familyRepository.save(Family.createFamily("WK" + String.format("%04d", seq)));
            seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                    senior, family, "서울시", String.format("%07d", seq), LocalDate.of(1950, 1, 1)));
            return senior.getId();
        });
    }

    private Long saveWalk(Long memberId, int goalSteps) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member senior = memberRepository.findById(memberId).orElseThrow();
            return walkRepository.save(Walk.createWithGoal(senior, LocalDate.now(), goalSteps)).getId();
        });
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
