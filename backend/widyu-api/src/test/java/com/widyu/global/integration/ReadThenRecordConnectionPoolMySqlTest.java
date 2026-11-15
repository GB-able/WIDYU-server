package com.widyu.global.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.widyu.album.Album;
import com.widyu.album.controller.AlbumController;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.home.controller.HomeController;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
 * 읽기 트랜잭션 안에서 열람 기록(REQUIRES_NEW)을 부르면 요청 하나가 커넥션 두 개를 쥔다. 동시 요청이 풀 크기를 넘으면
 * 모두 첫 커넥션을 쥔 채 두 번째를 기다리다 대기 한도(3000ms) 뒤 실패한다(#1056, #926과 같은 구조).
 * 앨범 상세와 보호자 홈 카드를 컨트롤러 진입점에서 동시에 불러 실패 수와 남은 기록 행 수를 센다.
 *
 * <p>풀은 4개로 줄이고 대기 한도는 운영과 같은 3000ms로 둔다. 동시 요청은 풀의 네 배인 16개 스레드로 보낸다.
 *
 * <p>실행: {@code POOL_MYSQL_URL=jdbc:mysql://127.0.0.1:<port>/pool_nested_<name> POOL_REDIS_PORT=<port>
 * DB_USERNAME=root DB_PASSWORD= ./gradlew :backend:widyu-api:test --tests '*ReadThenRecordConnectionPoolMySqlTest'}
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.datasource.hikari.maximum-pool-size=4",
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
@EnabledIfEnvironmentVariable(named = "POOL_MYSQL_URL", matches = ".+")
@Tag("pool-mysql")
@DisplayName("읽기 뒤 열람 기록 커넥션 풀 MySQL 재현")
class ReadThenRecordConnectionPoolMySqlTest {

    private static final int POOL = 4;
    private static final int THREADS = 16;
    private static final int REQUESTS = 32;
    private static final int RACE_ROUNDS = 20;
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final ThreadLocal<Long> CURRENT_MEMBER = new ThreadLocal<>();

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;
    @MockBean private MemberUtil memberUtil;

    @Autowired private AlbumController albumController;
    @Autowired private HomeController homeController;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMembershipRepository familyMembershipRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        String url = System.getenv("POOL_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/pool_nested_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("루프백의 일회용 pool_nested_ 데이터베이스만 허용합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> envOr("DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> envOr("DB_PASSWORD", ""));
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> envOr("POOL_REDIS_PORT", "6379"));
    }

    @Test
    @DisplayName("보호자 32명이 같은 앨범 상세를 처음 동시에 열어도 커넥션 대기로 실패하지 않고 조회 기록과 조회수가 32건 남는다")
    void 보호자_32명이_앨범_상세를_동시에_처음_열어도_실패하지_않는다() throws Exception {
        // given
        Family family = saveFamily();
        Long seniorId = saveSenior(family);
        Long albumId = saveAlbum(seniorId);
        List<Long> guardianIds = saveGuardians(family, REQUESTS);
        givenCurrentMemberFromThread();

        // when
        Map<String, Integer> outcomes = callAtOnce(guardianIds, guardianId -> albumController.getAlbumDetail(albumId));

        // then
        int rows = count("SELECT COUNT(*) FROM album_view WHERE album_id = ?", albumId);
        int viewCount = count("SELECT view_count FROM album WHERE album_id = ?", albumId);
        System.out.printf("[POOL-ALBUM-DETAIL] pool=%d threads=%d requests=%d outcomes=%s albumViewRows=%d viewCount=%d%n",
                POOL, THREADS, REQUESTS, outcomes, rows, viewCount);
        assertThat(outcomes).containsOnlyKeys("OK");
        assertThat(rows).isEqualTo(REQUESTS);
        assertThat(viewCount).isEqualTo(REQUESTS);
    }

    @Test
    @DisplayName("보호자 32명이 같은 시니어의 홈 카드를 동시에 열어도 커넥션 대기로 실패하지 않고 위치 열람 기록이 32건 남는다")
    void 보호자_32명이_홈_카드를_동시에_열어도_열람_기록이_빠지지_않는다() throws Exception {
        // given
        Family family = saveFamily();
        Long seniorId = saveSenior(family);
        List<Long> guardianIds = saveGuardians(family, REQUESTS);
        givenCurrentMemberFromThread();

        // when
        Map<String, Integer> outcomes = callAtOnce(guardianIds, guardianId -> homeController.getGuardianHomeCards(seniorId));

        // then
        int rows = count("SELECT COUNT(*) FROM location_access_log WHERE senior_member_id = ?", seniorId);
        System.out.printf("[POOL-HOME-CARDS] pool=%d threads=%d requests=%d outcomes=%s accessLogRows=%d missing=%d%n",
                POOL, THREADS, REQUESTS, outcomes, rows, REQUESTS - rows);
        assertThat(outcomes).containsOnlyKeys("OK");
        assertThat(rows).isEqualTo(REQUESTS);
    }

    @Test
    @DisplayName("같은 보호자가 같은 앨범을 처음 두 번 동시에 열어도 두 요청 모두 성공하고 조회 기록은 한 건만 남는다")
    void 같은_보호자의_첫_조회가_겹쳐도_상세_조회가_실패하지_않는다() throws Exception {
        // given
        Family family = saveFamily();
        Long seniorId = saveSenior(family);
        Long guardianId = saveGuardians(family, 1).getFirst();
        givenCurrentMemberFromThread();

        // when
        Map<String, Integer> outcomes = new TreeMap<>();
        List<Long> albumIds = new ArrayList<>();
        for (int i = 0; i < RACE_ROUNDS; i++) {
            Long albumId = saveAlbum(seniorId);
            albumIds.add(albumId);
            openTwiceAtOnce(guardianId, albumId).forEach(outcome -> outcomes.merge(outcome, 1, Integer::sum));
        }

        // then
        int rows = count("SELECT COUNT(*) FROM album_view WHERE member_id = ?", guardianId);
        int counted = count("SELECT COALESCE(SUM(view_count), 0) FROM album WHERE album_id IN ("
                + String.join(",", albumIds.stream().map(String::valueOf).toList()) + ")");
        System.out.printf("[POOL-ALBUM-FIRST-VIEW-RACE] rounds=%d outcomes=%s albumViewRows=%d viewCountSum=%d%n",
                RACE_ROUNDS, outcomes, rows, counted);
        assertThat(outcomes).containsOnlyKeys("OK");
        assertThat(rows).isEqualTo(RACE_ROUNDS);
        assertThat(counted).isEqualTo(RACE_ROUNDS);
    }

    /** 실제 MemberUtil처럼 요청마다 회원을 DB에서 읽는다. 누구의 요청인지는 호출 스레드에 둔다. */
    private void givenCurrentMemberFromThread() {
        given(memberUtil.getCurrentMember())
                .willAnswer(invocation -> memberRepository.findById(CURRENT_MEMBER.get()).orElseThrow());
    }

    /** 호출자마다 한 요청씩 16개 스레드에 던져 한꺼번에 출발시킨다. 결과는 성공이면 "OK", 실패면 예외 클래스 이름이다. */
    private Map<String, Integer> callAtOnce(List<Long> callerIds, Consumer<Long> call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (Long callerId : callerIds) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return callAs(callerId, () -> call.accept(callerId));
                }));
            }
            long startedAt = System.nanoTime();
            start.countDown();
            Map<String, Integer> outcomes = new TreeMap<>();
            for (Future<String> future : futures) {
                outcomes.merge(future.get(5, TimeUnit.MINUTES), 1, Integer::sum);
            }
            System.out.printf("[POOL-ELAPSED] requests=%d elapsedMs=%d%n",
                    callerIds.size(), (System.nanoTime() - startedAt) / 1_000_000);
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<String> openTwiceAtOnce(Long guardianId, Long albumId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return callAs(guardianId, () -> albumController.getAlbumDetail(albumId));
                }));
            }
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static String callAs(Long memberId, Runnable call) {
        CURRENT_MEMBER.set(memberId);
        try {
            call.run();
            return "OK";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        } finally {
            CURRENT_MEMBER.remove();
        }
    }

    private Family saveFamily() {
        int seq = SEQ.incrementAndGet();
        return new TransactionTemplate(transactionManager).execute(status ->
                familyRepository.save(Family.createFamily("PN" + String.format("%04d", seq))));
    }

    private Long saveSenior(Family family) {
        int seq = SEQ.incrementAndGet();
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member senior = memberRepository.save(Member.createMember(MemberType.SENIOR, "시니어" + seq,
                    "018" + String.format("%08d", seq)));
            seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                    senior, family, "서울시", "PNI" + String.format("%04d", seq), LocalDate.of(1950, 1, 1)));
            return senior.getId();
        });
    }

    private List<Long> saveGuardians(Family family, int size) {
        List<Long> guardianIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int seq = SEQ.incrementAndGet();
            guardianIds.add(new TransactionTemplate(transactionManager).execute(status -> {
                Member guardian = memberRepository.save(Member.createMember(MemberType.GUARDIAN, "보호자" + seq,
                        "018" + String.format("%08d", seq)));
                familyMembershipRepository.save(FamilyMembership.createMembership(family, guardian));
                return guardian.getId();
            }));
        }
        return guardianIds;
    }

    private Long saveAlbum(Long writerId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member writer = memberRepository.findById(writerId).orElseThrow();
            return albumRepository.save(Album.createAlbumWithMetadata(writer, "사진",
                    new ArrayList<>(List.of("https://cdn.widyu.shop/albums/photos/1/p.jpg")),
                    new ArrayList<>(List.of("https://cdn.widyu.shop/albums/photos/1/p.jpg")),
                    new ArrayList<>(Arrays.asList((Integer) null)))).getId();
        });
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
