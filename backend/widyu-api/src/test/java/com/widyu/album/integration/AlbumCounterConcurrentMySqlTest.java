package com.widyu.album.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

import com.widyu.album.Album;
import com.widyu.album.application.AlbumCommentService;
import com.widyu.album.application.AlbumLikeService;
import com.widyu.album.application.AlbumPermissionService;
import com.widyu.album.dto.request.AlbumCommentCreateRequest;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
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
 * 좋아요·댓글 수 갱신이 동시 요청에서 유실되지 않고, 동시에 커밋된 앨범 삭제를 되돌리지 않는지 실제 MySQL에서 확인한다(#811).
 *
 * <p>겹침은 타이밍이 아니라 권한 검사 mock 안에서 만든다. 권한 검사는 서비스가 앨범을 읽은 직후에 불리므로,
 * 그 안에서 다른 트랜잭션을 끝내면 「앨범을 읽은 뒤 다른 요청이 먼저 커밋한」 상태가 매번 같게 재현된다.
 *
 * <p>실행: {@code ALBUM_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/album_race_<name> DB_USERNAME=root DB_PASSWORD=
 * ./gradlew :backend:widyu-api:test --tests 'com.widyu.album.integration.AlbumCounterConcurrentMySqlTest'}
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
@DisplayName("앨범 좋아요·댓글 수 동시 갱신 MySQL 재현")
class AlbumCounterConcurrentMySqlTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final ThreadLocal<Long> CURRENT_MEMBER = new ThreadLocal<>();
    private static final int CONCURRENT = 10;

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;
    @MockBean private MemberUtil memberUtil;
    @MockBean private AlbumPermissionService albumPermissionService;

    @Autowired private AlbumLikeService albumLikeService;
    @Autowired private AlbumCommentService albumCommentService;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("서로 다른 회원 10명이 같은 앨범을 읽은 뒤 동시에 좋아요하면 좋아요 수가 10 늘어난다")
    void 동시에_좋아요_10건이_오면_좋아요_수가_10_늘어난다() throws Exception {
        // given
        Long albumId = saveAlbum(saveMember(MemberType.GUARDIAN));
        List<Long> likers = saveMembers(CONCURRENT);
        CyclicBarrier allRead = new CyclicBarrier(CONCURRENT);
        actAsCurrentMember();
        willAnswer(invocation -> {
            allRead.await(10, TimeUnit.SECONDS);
            return null;
        }).given(albumPermissionService).checkFamilyAccess(any(), any());

        // when
        List<Object> outcomes = runConcurrently(likers, memberId -> albumLikeService.likeAlbum(albumId));

        // then
        assertThat(outcomes).containsOnly("OK");
        assertThat(column("like_count", albumId)).isEqualTo(CONCURRENT);
        assertThat(count("SELECT COUNT(*) FROM album_like WHERE album_id = ?", albumId)).isEqualTo(CONCURRENT);
    }

    @Test
    @DisplayName("서로 다른 회원 10명이 같은 앨범을 읽은 뒤 동시에 댓글을 달면 댓글 수가 10 늘어난다")
    void 동시에_댓글_10건이_오면_댓글_수가_10_늘어난다() throws Exception {
        // given
        Long albumId = saveAlbum(saveMember(MemberType.GUARDIAN));
        List<Long> commenters = saveMembers(CONCURRENT);
        CyclicBarrier allRead = new CyclicBarrier(CONCURRENT);
        actAsCurrentMember();
        willAnswer(invocation -> {
            allRead.await(10, TimeUnit.SECONDS);
            return null;
        }).given(albumPermissionService).checkFamilyAccess(any(), any());

        // when
        List<Object> outcomes = runConcurrently(commenters, memberId ->
                albumCommentService.createComment(albumId, new AlbumCommentCreateRequest("댓글", null)));

        // then
        assertThat(outcomes).containsOnly("OK");
        assertThat(column("comment_count", albumId)).isEqualTo(CONCURRENT);
        assertThat(count("SELECT COUNT(*) FROM album_comment WHERE album_id = ?", albumId)).isEqualTo(CONCURRENT);
    }

    @Test
    @DisplayName("좋아요가 앨범을 읽은 뒤 작성자의 삭제가 먼저 커밋되면 앨범은 삭제 상태로 남는다")
    void 좋아요_처리_중_삭제가_먼저_커밋되면_삭제_상태가_유지된다() throws Exception {
        // given
        Long albumId = saveAlbum(saveMember(MemberType.GUARDIAN));
        Long likerId = saveMember(MemberType.SENIOR);
        actAsCurrentMember();
        willAnswer(invocation -> {
            deleteInAnotherTransaction(albumId);
            return null;
        }).given(albumPermissionService).checkFamilyAccess(any(), any());

        // when
        List<Object> outcomes = runConcurrently(List.of(likerId), memberId -> albumLikeService.likeAlbum(albumId));

        // then
        assertThat(outcomes).containsOnly("OK");
        assertThat(status(albumId)).isEqualTo("DELETED");
        assertThat(column("like_count", albumId)).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글이 앨범을 읽은 뒤 작성자의 삭제가 먼저 커밋되면 앨범은 삭제 상태로 남는다")
    void 댓글_처리_중_삭제가_먼저_커밋되면_삭제_상태가_유지된다() throws Exception {
        // given
        Long albumId = saveAlbum(saveMember(MemberType.GUARDIAN));
        Long commenterId = saveMember(MemberType.SENIOR);
        actAsCurrentMember();
        willAnswer(invocation -> {
            deleteInAnotherTransaction(albumId);
            return null;
        }).given(albumPermissionService).checkFamilyAccess(any(), any());

        // when
        List<Object> outcomes = runConcurrently(List.of(commenterId), memberId ->
                albumCommentService.createComment(albumId, new AlbumCommentCreateRequest("댓글", null)));

        // then
        assertThat(outcomes).containsOnly("OK");
        assertThat(status(albumId)).isEqualTo("DELETED");
        assertThat(column("comment_count", albumId)).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제가 앨범을 읽은 뒤 좋아요가 먼저 커밋되면 삭제 후에도 좋아요 수가 남는다")
    void 삭제_처리_중_좋아요가_먼저_커밋되면_좋아요_수가_유지된다() throws Exception {
        // given
        Long albumId = saveAlbum(saveMember(MemberType.GUARDIAN));
        Long likerId = saveMember(MemberType.SENIOR);
        actAsCurrentMember();

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Album album = albumRepository.findById(albumId).orElseThrow();
            try {
                List<Object> outcomes = runConcurrently(List.of(likerId),
                        memberId -> albumLikeService.likeAlbum(albumId));
                assertThat(outcomes).containsOnly("OK");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            album.delete();
        });

        // then
        assertThat(status(albumId)).isEqualTo("DELETED");
        assertThat(column("like_count", albumId)).isEqualTo(1);
    }

    /** 회원마다 스레드 하나로 동작을 실행한다. 결과는 성공이면 "OK", 아니면 예외 자체다. */
    private List<Object> runConcurrently(List<Long> memberIds, LongConsumer action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(memberIds.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Long memberId : memberIds) {
                futures.add(executor.submit(() -> {
                    CURRENT_MEMBER.set(memberId);
                    try {
                        action.accept(memberId);
                        return "OK";
                    } catch (Exception e) {
                        return e;
                    } finally {
                        CURRENT_MEMBER.remove();
                    }
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

    /** 검사 대상 트랜잭션과 다른 스레드(연결)에서 앨범을 삭제하고 커밋까지 기다린다. */
    private void deleteInAnotherTransaction(Long albumId) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    albumRepository.findById(albumId).orElseThrow().delete())).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void actAsCurrentMember() {
        given(memberUtil.getCurrentMember())
                .willAnswer(invocation -> memberRepository.findById(CURRENT_MEMBER.get()).orElseThrow());
    }

    private List<Long> saveMembers(int size) {
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            memberIds.add(saveMember(MemberType.SENIOR));
        }
        return memberIds;
    }

    private Long saveMember(MemberType type) {
        int seq = SEQ.incrementAndGet();
        return new TransactionTemplate(transactionManager).execute(status -> memberRepository.save(
                Member.createMember(type, "회원" + seq, "010" + String.format("%08d", seq))).getId());
    }

    private Long saveAlbum(Long writerId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member writer = memberRepository.findById(writerId).orElseThrow();
            return albumRepository.save(Album.createAlbumWithMetadata(writer, "사진",
                    new ArrayList<>(List.of("https://cdn.widyu.shop/albums/photos/1/p.jpg")),
                    new ArrayList<>(List.of("https://cdn.widyu.shop/albums/photos/1/p.jpg")),
                    new ArrayList<>(java.util.Arrays.asList((Integer) null)))).getId();
        });
    }

    private int column(String name, Long albumId) {
        return jdbc.queryForObject("SELECT " + name + " FROM album WHERE album_id = ?", Integer.class, albumId);
    }

    private String status(Long albumId) {
        return jdbc.queryForObject("SELECT status FROM album WHERE album_id = ?", String.class, albumId);
    }

    private int count(String sql, Long albumId) {
        return jdbc.queryForObject(sql, Integer.class, albumId);
    }

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

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
