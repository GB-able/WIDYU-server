package com.widyu.album.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.widyu.album.Album;
import com.widyu.album.application.AlbumUnlockService;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
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
 * 같은 앨범 잠금 해제가 겹칠 때 진 요청이 503(교착)이 아니라 「이미 해제한 앨범」으로 끝나는지 실제 MySQL에서 확인한다(#1025).
 *
 * <p>수정 전에는 두 요청이 모두 포인트 이력 INSERT의 외래키 검사로 같은 {@code senior_profile} 행에 공유 잠금을 잡은 뒤,
 * 커밋 시점의 잔액 UPDATE(배타 잠금)를 서로 기다려 MySQL이 한쪽을 교착으로 끊었다.
 *
 * <p>실행: {@code ALBUM_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/album_race_<name> DB_USERNAME=root DB_PASSWORD=
 * ./gradlew :backend:widyu-api:test --tests 'com.widyu.album.integration.AlbumUnlockConcurrentMySqlTest'}
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
@EnabledIfEnvironmentVariable(named = "ALBUM_MYSQL_URL", matches = ".+")
@Tag("album-mysql")
@DisplayName("앨범 잠금 해제 동시 요청 MySQL 재현")
class AlbumUnlockConcurrentMySqlTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final long START_POINTS = 10_000L;
    private static final int ROUNDS = 20;

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;
    @MockBean private MemberUtil memberUtil;

    @Autowired private AlbumUnlockService albumUnlockService;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMembershipRepository familyMembershipRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ALBUM_MYSQL_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/album_race_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("루프백의 일회용 album_race_ 데이터베이스만 허용합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> envOr("DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> envOr("DB_PASSWORD", ""));
    }

    @Test
    @DisplayName("같은 앨범 해제 두 건을 동시에 보내면 한 건은 성공하고 다른 한 건은 이미 해제한 앨범 오류가 되며 포인트는 한 번만 차감된다")
    void 같은_앨범_해제가_동시에_오면_한_건만_성공하고_나머지는_이미_해제_오류다() throws Exception {
        // given
        Family family = saveFamily();
        Long seniorId = saveSenior(family);
        Long guardianId = saveGuardian(family);
        given(memberUtil.getCurrentMember())
                .willAnswer(invocation -> memberRepository.findById(seniorId).orElseThrow());

        // when
        List<List<Object>> rounds = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            Long albumId = saveAlbum(guardianId);
            rounds.add(unlockTwiceAtOnce(albumId));
        }

        // then
        for (List<Object> outcomes : rounds) {
            assertThat(outcomes).containsExactlyInAnyOrder("OK", ErrorCode.ALBUM_ALREADY_UNLOCKED);
        }
        assertThat(points(seniorId)).isEqualTo(START_POINTS - Album.UNLOCK_PRICE * ROUNDS);
        assertThat(count("SELECT COUNT(*) FROM album_unlock WHERE member_id = ?", seniorId)).isEqualTo(ROUNDS);
        assertThat(count("SELECT COUNT(*) FROM point_history ph JOIN senior_profile sp ON sp.id = ph.senior_profile_id"
                + " WHERE sp.member_id = ? AND ph.type = 'USE'", seniorId)).isEqualTo(ROUNDS);
    }

    @Test
    @DisplayName("먼저 들어온 해제가 커밋하기 전에 같은 앨범 해제가 겹치면 뒤 요청은 포인트 행 잠금을 기다린 뒤 이미 해제한 앨범 오류가 된다")
    void 앞_해제가_커밋하는_동안_겹친_해제는_잠금을_기다린_뒤_이미_해제_오류다() throws Exception {
        // given
        Family family = saveFamily();
        Long seniorId = saveSenior(family);
        Long guardianId = saveGuardian(family);
        Long albumId = saveAlbum(guardianId);
        given(memberUtil.getCurrentMember())
                .willAnswer(invocation -> memberRepository.findById(seniorId).orElseThrow());

        // when
        Object outcome = unlockWhileEarlierUnlockCommits(seniorId, albumId);

        // then
        assertThat(outcome).isEqualTo(ErrorCode.ALBUM_ALREADY_UNLOCKED);
        assertThat(points(seniorId)).isEqualTo(START_POINTS - Album.UNLOCK_PRICE);
        assertThat(count("SELECT COUNT(*) FROM album_unlock WHERE member_id = ?", seniorId)).isEqualTo(1);
    }

    /** 두 스레드가 장벽에서 함께 출발해 같은 앨범을 해제한다. 결과는 성공이면 "OK", 업무 오류면 그 코드, 그 밖은 예외 자체다. */
    private List<Object> unlockTwiceAtOnce(Long albumId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return unlock(albumId);
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 먼저 들어온 해제 역할의 연결이 시니어 포인트 행을 {@code FOR UPDATE}로 잡은 채 검사 대상 해제를 돌린다.
     * 검사 대상이 그 행 잠금을 기다리기 시작하면 해제 기록과 차감을 쓰고 커밋한다.
     */
    private Object unlockWhileEarlierUnlockCommits(Long seniorId, Long albumId) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection earlier = dataSource.getConnection()) {
            earlier.setAutoCommit(false);
            long earlierConnectionId = connectionId(earlier);
            try (PreparedStatement statement = earlier.prepareStatement(
                    "SELECT points FROM senior_profile WHERE member_id = ? FOR UPDATE")) {
                statement.setLong(1, seniorId);
                statement.executeQuery();
            }

            Future<Object> result = executor.submit(() -> unlock(albumId));
            awaitLockWaitOn(result, earlierConnectionId, "senior_profile");
            try (PreparedStatement statement = earlier.prepareStatement(
                    "INSERT INTO album_unlock (album_id, member_id, unlocked_at, created_at, updated_at)"
                            + " VALUES (?, ?, NOW(6), NOW(6), NOW(6))")) {
                statement.setLong(1, albumId);
                statement.setLong(2, seniorId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = earlier.prepareStatement(
                    "UPDATE senior_profile SET points = points - ?, version = version + 1 WHERE member_id = ?")) {
                statement.setLong(1, Album.UNLOCK_PRICE);
                statement.setLong(2, seniorId);
                statement.executeUpdate();
            }
            earlier.commit();
            return result.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object unlock(Long albumId) {
        try {
            albumUnlockService.unlockAlbum(albumId);
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode();
        } catch (Exception e) {
            return e;
        }
    }

    /**
     * 검사 대상이 앞 연결의 행 잠금을 기다리기 시작할 때까지 기다린다. 그 전에 끝나면 결과를 담아 실패시킨다.
     * 같은 MySQL 서버의 다른 세션 대기를 잘못 세지 않도록 막는 쪽이 앞 연결이고 이 DB의 해당 테이블인 대기만 센다.
     */
    private void awaitLockWaitOn(Future<Object> later, long blockingConnectionId, String table) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (later.isDone()) {
                throw new IllegalStateException("검사 대상 해제가 잠금 대기 전에 끝났습니다: " + later.get());
            }
            Integer waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits w"
                            + " JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID"
                            + " JOIN performance_schema.threads t ON t.THREAD_ID = w.BLOCKING_THREAD_ID"
                            + " WHERE t.PROCESSLIST_ID = ? AND l.OBJECT_SCHEMA = DATABASE() AND l.OBJECT_NAME = ?",
                    Integer.class, blockingConnectionId, table);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("검사 대상 해제가 " + table + " 행 잠금 대기에 들어가지 않았습니다.");
    }

    private static long connectionId(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CONNECTION_ID()");
             java.sql.ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private Family saveFamily() {
        int seq = SEQ.incrementAndGet();
        return new TransactionTemplate(transactionManager).execute(status ->
                familyRepository.save(Family.createFamily("AU" + String.format("%04d", seq))));
    }

    private Long saveSenior(Family family) {
        int seq = SEQ.incrementAndGet();
        Long seniorId = new TransactionTemplate(transactionManager).execute(status -> {
            Member senior = memberRepository.save(Member.createMember(MemberType.SENIOR, "시니어" + seq,
                    "010" + String.format("%08d", seq)));
            seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                    senior, family, "서울시", String.format("%07d", seq), LocalDate.of(1950, 1, 1)));
            return senior.getId();
        });
        jdbc.update("UPDATE senior_profile SET points = ? WHERE member_id = ?", START_POINTS, seniorId);
        return seniorId;
    }

    private Long saveGuardian(Family family) {
        int seq = SEQ.incrementAndGet();
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member guardian = memberRepository.save(Member.createMember(MemberType.GUARDIAN, "보호자" + seq,
                    "010" + String.format("%08d", seq)));
            familyMembershipRepository.save(FamilyMembership.createMembership(family, guardian));
            return guardian.getId();
        });
    }

    private Long saveAlbum(Long guardianId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member guardian = memberRepository.findById(guardianId).orElseThrow();
            return albumRepository.save(Album.createAlbumWithMetadata(guardian, "사진",
                    new ArrayList<>(List.of("https://cdn.widyu.shop/albums/photos/1/p.jpg")),
                    new ArrayList<>(List.of("https://cdn.widyu.shop/albums/photos/1/p.jpg")),
                    new ArrayList<>(java.util.Arrays.asList((Integer) null)))).getId();
        });
    }

    private long points(Long seniorId) {
        return jdbc.queryForObject("SELECT points FROM senior_profile WHERE member_id = ?", Long.class, seniorId);
    }

    private int count(String sql, Long seniorId) {
        return jdbc.queryForObject(sql, Integer.class, seniorId);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
