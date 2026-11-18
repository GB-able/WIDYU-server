package com.widyu.sensor.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.MemberRepository;
import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.application.ActivityStateService;
import com.widyu.sensor.dto.request.SensorBatchRequest;
import com.widyu.sensor.repository.SensorBatchRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 같은 회원의 같은 5초 구간에 워치 IMU 묶음 여러 개가 동시에 더해질 때 교착 없이 합계가 모두 남는지 실제 MySQL에서 확인한다(#1093, LLD-0138 5.1).
 *
 * <p>개발 서버에서 워치가 다시 연결되면 밀린 묶음이 센서 스레드 8개에서 동시에 처리됐다. REPEATABLE READ에서 아직 없는
 * 구간 행을 {@code SELECT ... FOR UPDATE}로 읽으면 빈자리 잠금이 걸리고, 두 트랜잭션이 같은 자리에 INSERT하며 교착했다.
 * 지금은 빈 행을 {@code ON DUPLICATE KEY UPDATE}로 먼저 만들고 있는 행만 잠근다. H2로는 재현되지 않는다.
 *
 * <p>실행: {@code ACTIVITY_MYSQL_URL=jdbc:mysql://127.0.0.1:<port>/activity_race_<name> DB_USERNAME=root DB_PASSWORD=
 * ./gradlew :backend:widyu-api:test --tests 'com.widyu.sensor.integration.ActivityWindowConcurrentRecordMySqlTest'}
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
@EnabledIfEnvironmentVariable(named = "ACTIVITY_MYSQL_URL", matches = ".+")
@Tag("activity-mysql")
@DisplayName("활동 구간 동시 저장 MySQL 재현")
class ActivityWindowConcurrentRecordMySqlTest {

    // 5초 경계에 맞춘 측정 시각. 회차마다 10초씩 옮겨 늘 아직 없는 구간에 더한다.
    private static final long T0 = 1_794_931_150_000L;
    private static final long DT_NS = 20_000_000L;
    private static final int THREADS = 8;
    private static final int ROUNDS = 30;
    // 묶음 하나는 1초(50Hz × 50개)이고 스레드 i의 묶음은 회차 시작 + i × 500ms에서 시작한다.
    private static final int SAMPLES = 50;
    private static final long SAMPLE_MS = 20L;
    private static final long WINDOW_MS = 5_000L;
    // 크기 1040mg → 1g를 뺀 40mg를 더한다.
    private static final int MAGNITUDE_MG = 1040;
    private static final long DEVIATION_MG = 40L;
    private static final String ORIGINAL = "01j8y000000000000000001093";
    private static final String RESEND = "01j8y000000000000000011093";

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;

    @Autowired private ActivityStateService activityStateService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SensorBatchRepository sensorBatchRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ACTIVITY_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/activity_race_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("루프백의 일회용 activity_race_ 데이터베이스만 허용합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> envOr("DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> envOr("DB_PASSWORD", ""));
    }

    @Test
    @DisplayName("같은 회원의 같은 새 구간에 묶음 8개를 동시에 더하면 교착 없이 샘플 수와 합계가 모두 더해진다")
    void 같은_새_구간에_묶음_8개를_동시에_더하면_교착_없이_합계가_모두_더해진다() throws Exception {
        // when
        Outcome outcome = recordRounds(30L, 0L);

        // then
        assertThat(outcome.deadlocks()).isZero();
        assertThat(outcome.windows()).isEqualTo(ROUNDS);
        assertThat(outcome.matched()).isEqualTo(ROUNDS);
    }

    @Test
    @DisplayName("구간 경계에 걸친 묶음이 섞여 두 새 구간에 동시에 더해도 교착 없이 구간마다 샘플 수와 합계가 모두 더해진다")
    void 구간_경계에_걸친_묶음이_섞여_동시에_더해도_합계가_모두_더해진다() throws Exception {
        // when: 홀수 스레드 묶음을 4초 밀어 스레드 1 묶음(4.5초~)이 앞 구간 25개, 뒤 구간 25개로 걸친다
        Outcome outcome = recordRounds(31L, 4_000L);

        // then
        assertThat(outcome.deadlocks()).isZero();
        assertThat(outcome.windows()).isEqualTo(2 * ROUNDS);
        assertThat(outcome.matched()).isEqualTo(2 * ROUNDS);
    }

    @Test
    @DisplayName("원 묶음이 커밋되기 전에 재전송 묶음의 구간 저장이 오면 그 커밋을 기다린 뒤 원 묶음을 보고 더하지 않는다")
    void 원_묶음이_커밋되기_전에_재전송_구간_저장이_오면_커밋을_기다린_뒤_더하지_않는다() throws Exception {
        // given: 원 묶음 행을 INSERT만 하고 커밋하지 않은 채 붙잡아 둔다. PK는 재전송보다 작다.
        Long memberId = new TransactionTemplate(transactionManager).execute(status -> memberRepository.save(
                Member.createMember(MemberType.SENIOR, "시니어", "01010931093")).getId());
        Member member = memberRepository.findById(memberId).orElseThrow();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> original = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        sensorBatchRepository.saveAndFlush(batch(member, ORIGINAL, null));
                        inserted.countDown();
                        await(release);
                    }));
            assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
            sensorBatchRepository.save(batch(member, RESEND, ORIGINAL));

            // when
            Future<?> resend = executor.submit(() -> activityStateService.record(memberId, RESEND, "imu_watch",
                    clock(), acc(0), new SensorBatchRequest.Resend(true, ORIGINAL, 1L, null, "s-1", T0)));
            awaitLockWaitOn("sensor_batch");
            release.countDown();
            original.get(30, TimeUnit.SECONDS);
            resend.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        // then
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_window WHERE member_id = ?",
                Integer.class, memberId)).isZero();
    }

    private record Outcome(long deadlocks, int windows, long matched) {
    }

    /**
     * 회차마다 스레드 8개가 장벽에서 함께 출발해 1초 묶음을 하나씩 더하고, 구간마다 샘플 수와 합계가 기대값과 같은지 센다.
     * 홀수 스레드 묶음은 {@code oddShiftMs}만큼 늦게 시작한다. 크기가 모두 같아 합계는 샘플 수 × 40mg다.
     */
    private Outcome recordRounds(long memberId, long oddShiftMs) throws Exception {
        long deadlocksBefore = deadlocks();
        Map<Long, Long> expected = new HashMap<>();
        for (int round = 0; round < ROUNDS; round++) {
            long roundStartMs = T0 + round * 10_000L;
            recordAtOnce(memberId, roundStartMs, oddShiftMs);
            for (int i = 0; i < THREADS; i++) {
                for (int j = 0; j < SAMPLES; j++) {
                    long tsMs = roundStartMs + offsetMs(i, oddShiftMs) + j * SAMPLE_MS;
                    expected.merge(Math.floorDiv(tsMs, WINDOW_MS) * WINDOW_MS, 1L, Long::sum);
                }
            }
        }
        long deadlocks = deadlocks() - deadlocksBefore;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT window_start_ms, sample_count, magnitude_sum_mg, magnitude_sq_sum_mg FROM activity_window"
                        + " WHERE member_id = ?", memberId);
        long matched = rows.stream().filter(row -> {
            long count = expected.getOrDefault(((Number) row.get("window_start_ms")).longValue(), -1L);
            return ((Number) row.get("sample_count")).longValue() == count
                    && ((Number) row.get("magnitude_sum_mg")).longValue() == count * DEVIATION_MG
                    && ((Number) row.get("magnitude_sq_sum_mg")).longValue() == count * DEVIATION_MG * DEVIATION_MG;
        }).count();
        long samples = rows.stream().mapToLong(row -> ((Number) row.get("sample_count")).longValue()).sum();
        System.out.printf("[activity-mysql] oddShiftMs=%d rounds=%d threads=%d deadlocks=%d windows=%d/%d"
                        + " matched=%d samples=%d/%d%n", oddShiftMs, ROUNDS, THREADS, deadlocks, rows.size(),
                expected.size(), matched, samples, (long) ROUNDS * THREADS * SAMPLES);
        return new Outcome(deadlocks, rows.size(), matched);
    }

    private static long offsetMs(int thread, long oddShiftMs) {
        if (thread % 2 == 1) {
            return thread * 500L + oddShiftMs;
        }
        return thread * 500L;
    }

    private void recordAtOnce(long memberId, long roundStartMs, long oddShiftMs) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CyclicBarrier barrier = new CyclicBarrier(THREADS);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                long batchOffsetMs = roundStartMs - T0 + offsetMs(i, oddShiftMs);
                String batchId = "batch-" + memberId + "-" + roundStartMs + "-" + i;
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    activityStateService.record(memberId, batchId, "imu_watch", clock(), acc(batchOffsetMs), null);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /** 이 DB의 해당 테이블에서 행 잠금 대기가 생길 때까지 기다린다. */
    private void awaitLockWaitOn(String table) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits w"
                            + " JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID"
                            + " WHERE l.OBJECT_SCHEMA = DATABASE() AND l.OBJECT_NAME = ?",
                    Integer.class, table);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("재전송 구간 저장이 " + table + " 잠금 대기에 들어가지 않았습니다.");
    }

    private long deadlocks() {
        return jdbc.queryForObject(
                "SELECT `count` FROM information_schema.INNODB_METRICS WHERE name = 'lock_deadlocks'", Long.class);
    }

    private static SensorBatch batch(Member member, String batchId, String originalBatchId) {
        return SensorBatch.builder()
                .batchId(batchId).member(member).stream("imu_watch").source("watch")
                .deviceId("watch-1").sessionId("s-1").seq(1L).bootId("b1").clockMappingId("cm-1")
                .anchorElapsedNs(0L).anchorEpochMs(T0).uncertaintyMs(1.0)
                .measuredAtStartMs(T0).measuredAtEndMs(T0 + 1_000)
                .serverReceivedAtMs(T0).acceptedAtMs(T0).persistedAtMs(T0).modelAvailableAtServerMs(T0)
                .onBody(true).qualityStatus("OK").gyroBackfill(false)
                .isResend(originalBatchId != null).originalBatchId(originalBatchId)
                .configMismatch(false).s3Key("sensor/" + batchId + ".json").byteSize(256)
                .payloadSha256("b".repeat(64))
                .build();
    }

    private static SensorBatchRequest.Clock clock() {
        return SensorBatchRequest.Clock.of("b1", "cm-1", "0", T0, 1.0);
    }

    /** offsetMs부터 20ms 간격으로 z축 크기만 있는 샘플 50개. */
    private static SensorBatchRequest.Axis acc(long offsetMs) {
        List<List<Integer>> mg = new ArrayList<>(Collections.nCopies(SAMPLES, List.of(0, 0, MAGNITUDE_MG)));
        List<Long> dtNs = new ArrayList<>(Collections.nCopies(SAMPLES - 1, DT_NS));
        return new SensorBatchRequest.Axis(50.0, SAMPLES, String.valueOf(offsetMs * 1_000_000L), dtNs, mg, null);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
