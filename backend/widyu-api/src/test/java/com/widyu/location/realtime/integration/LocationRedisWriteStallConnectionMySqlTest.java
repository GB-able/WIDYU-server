package com.widyu.location.realtime.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.location.realtime.application.RealtimeLocationService;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Redis 쓰기가 멈춘 동안 위치 요청이 MySQL 커넥션을 쥐고 기다리지 않는지 실제 MySQL·Redis에서 잰다(LLD-0121).
 *
 * <p>실시간 반영 트랜잭션 안에서 Redis 쓰기(최신 위치·이동 경로)를 하면, Redis가 느릴 때 그동안 커넥션을 쥔다.
 * {@code CLIENT PAUSE ... WRITE}는 읽기는 통과시키고 쓰기만 멈추므로, 같은 자리 위치(트랜잭션 안 Redis 명령이
 * 체류 GET뿐)라면 트랜잭션은 끝나고 트랜잭션 밖의 쓰기만 기다리게 된다. 멈춘 동안 Hikari 활성 커넥션 수를 반복해
 * 잰다. 백그라운드 작업(스케줄러 등)이 잠깐 커넥션을 쓸 수 있어 1개까지는 허용한다.
 *
 * <p>실행: {@code LOCATION_MYSQL_URL=jdbc:mysql://127.0.0.1:<port>/location_burst_<name> LOCATION_REDIS_PORT=<port>
 * DB_USERNAME=root DB_PASSWORD= ./gradlew :backend:widyu-api:test --tests '*LocationRedisWriteStallConnectionMySqlTest'}
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.connection-timeout=3000",
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
@EnabledIfEnvironmentVariable(named = "LOCATION_MYSQL_URL", matches = ".+")
@Tag("location-mysql")
@DisplayName("Redis 쓰기 정지 중 위치 요청의 DB 커넥션 점유 MySQL 측정")
class LocationRedisWriteStallConnectionMySqlTest {

    private static final int REQUESTS = 8;
    private static final long PAUSE_MS = 2_000;
    private static final long SAMPLE_FROM_MS = 300;
    private static final long SAMPLE_UNTIL_MS = 1_700;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;

    @Autowired private RealtimeLocationService realtimeLocationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private DataSource dataSource;
    @Autowired private RedisConnectionFactory redisConnectionFactory;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        String url = System.getenv("LOCATION_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/location_burst_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("루프백의 일회용 location_burst_ 데이터베이스만 허용합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> envOr("DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> envOr("DB_PASSWORD", ""));
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> envOr("LOCATION_REDIS_PORT", "6379"));
    }

    @Test
    @DisplayName("Redis 쓰기가 멈춘 동안 같은 자리 위치 요청은 DB 커넥션을 쥐고 기다리지 않는다")
    void Redis_쓰기가_멈춘_동안_같은_자리_위치_요청은_DB_커넥션을_쥐지_않는다() throws Exception {
        // given
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            Long memberId = saveSenior();
            realtimeLocationService.updateAndBroadcast(payload(memberId), memberId);  // 체류 정보를 먼저 만든다
            memberIds.add(memberId);
        }
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();

        // when
        pauseRedisWrites();
        long pausedAt = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        List<Future<String>> futures = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (Long memberId : memberIds) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    realtimeLocationService.updateAndBroadcast(payload(memberId), memberId);
                    return null;
                } catch (Exception e) {
                    return e.getClass().getSimpleName();
                }
            }));
        }
        start.countDown();
        int maxActive = 0;
        int samples = 0;
        while (elapsedMs(pausedAt) < SAMPLE_UNTIL_MS) {
            if (elapsedMs(pausedAt) >= SAMPLE_FROM_MS) {
                maxActive = Math.max(maxActive, pool.getActiveConnections());
                samples++;
            }
            Thread.sleep(20);
        }
        List<String> failures = new ArrayList<>();
        for (Future<String> future : futures) {
            String failure = future.get(30, TimeUnit.SECONDS);
            if (failure != null) {
                failures.add(failure);
            }
        }
        long elapsedMs = elapsedMs(pausedAt);
        executor.shutdown();
        System.out.printf("[LOCATION-REDIS-STALL] requests=%d pauseMs=%d samples=%d maxActiveDuringPause=%d "
                + "failures=%s elapsedMs=%d%n", REQUESTS, PAUSE_MS, samples, maxActive, failures, elapsedMs);

        // then
        assertThat(failures).isEmpty();
        assertThat(maxActive).isLessThanOrEqualTo(1);
    }

    private void pauseRedisWrites() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.execute("CLIENT", "PAUSE".getBytes(UTF_8), String.valueOf(PAUSE_MS).getBytes(UTF_8),
                    "WRITE".getBytes(UTF_8));
        }
    }

    private Long saveSenior() {
        int n = SEQ.incrementAndGet();
        Family family = familyRepository.save(Family.createFamily("RS" + String.format("%04d", n)));
        Member member = memberRepository.save(
                Member.createMember(MemberType.SENIOR, "시니어" + n, "018" + String.format("%08d", n)));
        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                member, family, "서울", "RSI" + String.format("%04d", n), LocalDate.of(1950, 1, 1)));
        return member.getId();
    }

    /** 측정 시각이 없는 기존 형식: 항상 실시간 반영 대상이고 원본은 저장하지 않는다. */
    private static byte[] payload(Long memberId) {
        return """
                {"memberId":%d,"latitude":37.55,"longitude":126.97}""".formatted(memberId).getBytes(UTF_8);
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
