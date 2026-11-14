package com.widyu.location.realtime.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.location.SeniorLocation;
import com.widyu.location.realtime.application.LocationIngestService;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.stay.application.SeniorStayStateService;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 수신 조율, 판정, 반영을 나눈 뒤에도 실제 Spring 프록시와 트랜잭션에서 경계가 그대로인지 본다(LLD-0142, LLD-0143).
 * 판정 트랜잭션은 Redis 최신 위치 저장 전에 커밋되고 커넥션을 돌려주며, 판정이 기술 오류로 실패하면 성공 ACK가 나가지
 * 않는다. 위치 원본은 새로 저장하지 않고 이미 있던 행은 그대로다. Redis 최신 위치 저장소와 이동 경로 쓰기만 대역이다(H2).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
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
@DisplayName("위치 수신 트랜잭션 경계")
class LocationIngestTransactionBoundaryTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;
    @MockBean private SeniorLocationRepository seniorLocationRepository;
    // 이동 경로 쓰기만 막는다. 빈을 통째로 대역으로 바꾸면 Redis 저장소 설정이 깨진다.
    @SpyBean(name = "redisTemplate") private RedisTemplate<String, Object> redisTemplate;
    @SpyBean private SeniorStayStateService stayStateService;

    @Autowired private LocationIngestService ingestService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void 이동_경로_쓰기를_막는다() {
        doReturn(mock(ListOperations.class)).when(redisTemplate).opsForList();
        doReturn(true).when(redisTemplate).expire(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("판정 중에는 트랜잭션이 열려 있고, Redis 최신 위치를 저장할 때는 판정이 이미 커밋돼 커넥션을 하나도 쥐지 않는다")
    void 판정_트랜잭션은_최신_위치_저장_전에_커밋되고_커넥션을_돌려준다() throws Exception {
        // given
        Long seniorId = senior();
        insertExistingFix(seniorId);
        List<Boolean> txActiveInJudge = new ArrayList<>();
        willAnswer(invocation -> {
            txActiveInJudge.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).given(stayStateService).find(seniorId);
        List<Object> atProjection = new ArrayList<>();
        willAnswer(invocation -> {
            atProjection.add(TransactionSynchronizationManager.isActualTransactionActive());
            atProjection.add(activeConnections());
            atProjection.add(stayRows(seniorId)); // 다른 커넥션에서 보이면 커밋된 것이다
            return invocation.getArgument(0);
        }).given(seniorLocationRepository).save(any(SeniorLocation.class));

        // when
        LocationAckResponse ack = ingestService.receive(payload(seniorId), seniorId);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.APPLIED);
        assertThat(txActiveInJudge).containsExactly(true);
        assertThat(atProjection).containsExactly(false, 0, 1);
        assertThat(fixRows(seniorId)).isEqualTo(1); // 이미 있던 행 하나 그대로, 새 행 없음
        assertThat(activeConnectionsAfterAsync()).isZero();
    }

    @Test
    @DisplayName("판정이 DB 기술 오류로 실패하면 상태, 최신 위치, 위치 원본 어느 것도 남지 않고 성공 ACK 대신 오류가 나며 커넥션을 모두 돌려준다")
    void 판정이_기술_오류로_실패하면_아무것도_남지_않고_ACK가_없다() throws Exception {
        // given
        Long seniorId = senior();
        willThrow(new QueryTimeoutException("DB timeout")).given(stayStateService).find(seniorId);

        // when & then
        assertThatThrownBy(() -> ingestService.receive(payload(seniorId), seniorId))
                .isInstanceOf(QueryTimeoutException.class);
        assertThat(fixRows(seniorId)).isZero();
        assertThat(stayRows(seniorId)).isZero();
        then(seniorLocationRepository).should(never()).save(any(SeniorLocation.class));
        assertThat(activeConnections()).isZero();
    }

    @Test
    @DisplayName("시니어가 아닌 회원의 위치는 판정 트랜잭션의 업무 거절이 그대로 전달돼 REJECTED ACK를 받는다")
    void 시니어가_아니면_원본_없이_REJECTED() throws Exception {
        // given
        int n = SEQ.incrementAndGet();
        Member guardian = memberRepository.save(
                Member.createMember(MemberType.GUARDIAN, "보호자" + n, "0177" + String.format("%07d", n)));

        // when
        LocationAckResponse ack = ingestService.receive(payload(guardian.getId()), guardian.getId());

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.REJECTED);
        assertThat(fixRows(guardian.getId())).isZero();
        assertThat(activeConnections()).isZero();
    }

    @Test
    @DisplayName("시니어가 아닌 회원의 오래된 위치는 판정을 건너뛰어도 읽기 트랜잭션의 시니어 확인으로 REJECTED이고 커넥션을 돌려준다")
    void 시니어가_아니면_오래된_위치도_REJECTED() throws Exception {
        // given
        int n = SEQ.incrementAndGet();
        Member guardian = memberRepository.save(
                Member.createMember(MemberType.GUARDIAN, "보호자" + n, "0177" + String.format("%07d", n)));
        byte[] old = new String(payload(guardian.getId()), UTF_8)
                .replaceFirst("\"ts_ms\":\\d+", "\"ts_ms\":" + (System.currentTimeMillis() - 20 * 60_000L))
                .getBytes(UTF_8);

        // when
        LocationAckResponse ack = ingestService.receive(old, guardian.getId());

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.REJECTED);
        assertThat(activeConnections()).isZero();
    }

    /** 위치 원본을 저장하던 때 쌓인 과거 행. 신규 수신이 늘리지도 지우지도 않는지 본다(LLD-0143). */
    private void insertExistingFix(Long memberId) {
        jdbc.update("INSERT INTO location_fix (member_id, device_id, session_id, seq, ts_ms, lat, lon, is_mock, reason, "
                        + "server_received_at_ms, accepted_at_ms, persisted_at_ms, payload, payload_sha256, created_at, "
                        + "updated_at) VALUES (?, 'old-device', 'old-session', 1, 1, 37.5, 127.0, false, 'move', 1, 1, 1, "
                        + "'{}', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                memberId, "0".repeat(64));
    }

    /** 위치 이벤트의 비동기 리스너(건강 일정 도착 처리)가 다른 스레드에서 잠깐 커넥션을 쓰므로 끝날 때까지 기다린다. */
    private int activeConnectionsAfterAsync() throws Exception {
        for (int i = 0; i < 100 && activeConnections() > 0; i++) {
            Thread.sleep(20);
        }
        return activeConnections();
    }

    private int activeConnections() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
    }

    private int stayRows(Long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM senior_stay_state WHERE member_id = ?", Integer.class, memberId);
    }

    private int fixRows(Long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM location_fix WHERE member_id = ?", Integer.class, memberId);
    }

    private Long senior() {
        int n = SEQ.incrementAndGet();
        Family family = familyRepository.save(Family.createFamily("TB" + String.format("%04d", n)));
        Member senior = memberRepository.save(
                Member.createMember(MemberType.SENIOR, "시니어" + n, "0167" + String.format("%07d", n)));
        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                senior, family, "서울", "TBI" + String.format("%04d", n), LocalDate.of(1950, 1, 1)));
        return senior.getId();
    }

    private static byte[] payload(Long memberId) {
        return """
            {"v":2,"memberId":%d,"device_id":"ph-tb-%d","session_id":"s-tb","seq":%d,
             "lat":37.5551,"lon":126.9707,"accuracy_m":8.5,"speed_mps":0.9,"provider":"fused",
             "is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(memberId, memberId, SEQ.incrementAndGet(), System.currentTimeMillis())
                .getBytes(UTF_8);
    }
}
