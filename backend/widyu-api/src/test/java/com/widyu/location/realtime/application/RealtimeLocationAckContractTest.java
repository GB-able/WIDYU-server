package com.widyu.location.realtime.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.widyu.location.SeniorLocation;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationSource;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * v2 위치 ACK가 실제 실시간 처리 결과를 말하는지 본다(LLD-0143). 위치 원본은 저장하지 않는다.
 * {@code APPLIED}는 반영, {@code SKIPPED}는 정상 생략, {@code REJECTED}는 요청 오류이고, 기술 오류와 대기 초과는 ACK가 없다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("위치 ACK 처리 결과 계약")
class RealtimeLocationAckContractTest {

    @Mock private SeniorLocationRepository seniorLocationRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private ParentLocationRepository parentLocationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ListOperations<String, Object> listOperations;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SafeZoneAlertService safeZoneAlertService;
    @Mock private LocationAccessLogService locationAccessLogService;
    @Mock private MemberRepository memberRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Spy private MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 3_000);
    @Spy private FakeSeniorStayStateService stayStateService = new FakeSeniorStayStateService();

    @InjectMocks private LocationJudgeService judgeService;
    @InjectMocks private LocationProjectionService projectionService;

    private LocationIngestService ingestService() {
        return LocationServiceFixture.ingest(validator, sequencer, judgeService, projectionService, transactionManager);
    }

    @Test
    @DisplayName("판정하고 최신 위치에 반영한 위치는 APPLIED ACK를 돌려주고 요청 식별자를 그대로 싣는다")
    void 반영한_위치는_APPLIED() {
        // given
        stubSenior(1L);
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());

        // when
        LocationAckResponse ack = ingestService().receive(payload(1L, System.currentTimeMillis()), 1L);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.APPLIED);
        assertThat(ack.location().locationSource()).isEqualTo(LocationSource.LATEST);
        assertThat(ack.deviceId()).isEqualTo("ph-1");
        assertThat(ack.sessionId()).isEqualTo("s-1");
        assertThat(ack.seq()).isEqualTo(7L);
        then(seniorLocationRepository).should().save(any(SeniorLocation.class));
    }

    @Test
    @DisplayName("이미 반영한 위치를 다시 보내면 판정과 방송 없이 SKIPPED ACK를 돌려준다")
    void 같은_위치_재전송은_SKIPPED() {
        // given
        long tsMs = System.currentTimeMillis() - 1_000L;
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.of(SeniorLocation.of(1L, 37.5, 127.0, tsMs)));
        given(seniorProfileRepository.existsByMemberId(1L)).willReturn(true);

        // when
        LocationAckResponse ack = ingestService().receive(payload(1L, tsMs), 1L);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.SKIPPED);
        assertThat(ack.location().locationSource()).isEqualTo(LocationSource.REQUEST);
        then(seniorProfileRepository).should(never()).findByMemberId(any());
        then(seniorLocationRepository).should(never()).save(any(SeniorLocation.class));
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("15분 넘게 지난 위치는 최신 위치를 읽지 않고 SKIPPED ACK를 돌려준다")
    void 오래된_위치는_SKIPPED() {
        // given
        given(seniorProfileRepository.existsByMemberId(1L)).willReturn(true);

        // when
        LocationAckResponse ack = ingestService().receive(payload(1L, System.currentTimeMillis() - 16 * 60_000L), 1L);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.SKIPPED);
        then(seniorLocationRepository).should(never()).findById(any());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("시니어가 아닌 회원의 위치는 실시간 반영을 건너뛰는 오래된 위치여도 생략이 아니라 REJECTED다")
    void 시니어가_아니면_건너뛰는_위치도_REJECTED() {
        // given
        given(seniorProfileRepository.existsByMemberId(1L)).willReturn(false);

        // when
        LocationAckResponse ack = ingestService().receive(payload(1L, System.currentTimeMillis() - 16 * 60_000L), 1L);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.REJECTED);
        assertThat(ack.location()).isNull();
    }

    @Test
    @DisplayName("다른 회원의 위치는 최신 위치를 읽기 전에 REJECTED로 거절하고 방송하지 않는다")
    void 다른_회원의_위치는_REJECTED() {
        // when
        LocationAckResponse ack = ingestService().receive(payload(2L, System.currentTimeMillis()), 1L);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.REJECTED);
        then(seniorLocationRepository).should(never()).findById(any());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("reason이 정해진 세 값 밖이거나 위도가 ±90을 벗어나면 판정 없이 REJECTED다")
    void 입력이_잘못되면_REJECTED() {
        // given
        byte[] badReason = """
                {"v":2,"memberId":1,"device_id":"ph-1","session_id":"s-1","seq":7,
                 "lat":37.5,"lon":127.0,"is_mock":false,"ts_ms":%d,"reason":"walk"}"""
                .formatted(System.currentTimeMillis()).getBytes(UTF_8);
        byte[] badLatitude = """
                {"v":2,"memberId":1,"device_id":"ph-1","session_id":"s-1","seq":7,
                 "lat":91.0,"lon":127.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(System.currentTimeMillis()).getBytes(UTF_8);

        // when
        LocationAckResponse reason = ingestService().receive(badReason, 1L);
        LocationAckResponse latitude = ingestService().receive(badLatitude, 1L);

        // then
        assertThat(reason.result()).isEqualTo(LocationAckResult.REJECTED);
        assertThat(latitude.result()).isEqualTo(LocationAckResult.REJECTED);
        then(seniorLocationRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("앱이 회차를 실어 보내도 위치는 회차에 귀속하지 않고 실시간 처리 결과로 ACK한다")
    void 회차를_실어_보내도_실시간_결과로_ACK한다() {
        // given
        stubSenior(1L);
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        byte[] payload = """
                {"v":2,"memberId":1,"device_id":"ph-1","session_id":"s-1","seq":7,"run_id":"run-other",
                 "lat":37.5,"lon":127.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(System.currentTimeMillis()).getBytes(UTF_8);

        // when
        LocationAckResponse ack = ingestService().receive(payload, 1L);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.APPLIED);
    }

    @Test
    @DisplayName("최신 위치를 읽다 Redis 장애가 나면 ACK 없이 예외를 전달하고 방송하지 않는다")
    void 최신_위치_조회_Redis_장애면_ACK가_없다() {
        // given
        given(seniorLocationRepository.findById(1L)).willThrow(new RedisConnectionFailureException("down"));

        // when & then
        assertThatThrownBy(() -> ingestService().receive(payload(1L, System.currentTimeMillis()), 1L))
                .isInstanceOf(RedisConnectionFailureException.class);
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("판정 트랜잭션에서 DB 오류가 나면 ACK 없이 예외를 전달하고 최신 위치와 방송을 남기지 않는다")
    void 판정_DB_오류면_ACK가_없다() {
        // given
        stubSenior(1L);
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        willThrow(new QueryTimeoutException("DB timeout")).given(stayStateService).find(1L);

        // when & then
        assertThatThrownBy(() -> ingestService().receive(payload(1L, System.currentTimeMillis()), 1L))
                .isInstanceOf(QueryTimeoutException.class);
        then(seniorLocationRepository).should(never()).save(any(SeniorLocation.class));
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("최신 위치 저장이 실패한 위치를 다시 보내면 반영이 복구돼 APPLIED이고 방송은 한 번이다")
    void 최신_위치_저장_실패는_재전송으로_복구된다() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis());
        stubSenior(1L);
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        willThrow(new RedisConnectionFailureException("down"))
                .willReturn(null)
                .given(seniorLocationRepository).save(any(SeniorLocation.class));
        assertThatThrownBy(() -> ingestService().receive(payload, 1L))
                .isInstanceOf(RedisConnectionFailureException.class);

        // when
        LocationAckResponse resent = ingestService().receive(payload, 1L);

        // then
        assertThat(resent.result()).isEqualTo(LocationAckResult.APPLIED);
        assertThat(resent.location().stayStartTime()).isNotNull();
        then(seniorLocationRepository).should(times(2)).save(any(SeniorLocation.class));
        then(messagingTemplate).should(times(1))
                .convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
    }

    @Test
    @DisplayName("같은 회원 위치가 몰려 실시간 차례를 얻지 못하면 ACK를 보내지 않는다")
    void 실시간_대기_초과면_ACK가_없다() {
        // given
        willThrow(new LocationSequenceBusyException()).given(sequencer).run(eq(1L), any(), any(), any());

        // when
        LocationAckResponse ack = ingestService().receive(payload(1L, System.currentTimeMillis()), 1L);

        // then
        assertThat(ack).isNull();
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("같은 회원의 더 늦게 잰 위치가 반영돼 밀린 위치는 판정 없이 SKIPPED ACK를 돌려준다")
    void 더_최근_위치에_밀린_위치는_SKIPPED() {
        // given
        willAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .given(sequencer).run(eq(1L), any(), any(), any());
        given(seniorProfileRepository.existsByMemberId(1L)).willReturn(true);

        // when
        LocationAckResponse ack = ingestService().receive(payload(1L, System.currentTimeMillis()), 1L);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.SKIPPED);
        assertThat(ack.seq()).isEqualTo(7L);
        then(seniorLocationRepository).should(never()).findById(any());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("미래로 잰 위치는 서버 수신 시각으로 한 번 자른 같은 값으로 순서를 판정하고 최신 위치에 저장한다")
    void 미래로_잰_위치는_한_번_자른_같은_시각으로_순서_판정과_저장을_한다() {
        // given
        long oneDayLater = System.currentTimeMillis() + 86_400_000L;
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        stubSenior(1L);

        // when
        LocationAckResponse ack = ingestService().receive(payload(1L, oneDayLater), 1L);

        // then
        ArgumentCaptor<Long> orderAt = ArgumentCaptor.forClass(Long.class);
        then(sequencer).should().run(eq(1L), orderAt.capture(), any(), any());
        ArgumentCaptor<SeniorLocation> saved = ArgumentCaptor.forClass(SeniorLocation.class);
        then(seniorLocationRepository).should().save(saved.capture());
        assertThat(ack.result()).isEqualTo(LocationAckResult.APPLIED);
        assertThat(orderAt.getValue()).isLessThanOrEqualTo(System.currentTimeMillis());
        assertThat(saved.getValue().getMeasuredAtMs()).isEqualTo(orderAt.getValue());
    }

    private void stubSenior(Long memberId) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ReflectionTestUtils.setField(member, "id", memberId);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        given(seniorProfileRepository.findByMemberId(memberId)).willReturn(Optional.of(seniorProfile));
        lenient().when(parentLocationRepository.findAllByMember(member)).thenReturn(List.of());
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    private static byte[] payload(Long memberId, long tsMs) {
        return """
                {"v":2,"memberId":%d,"device_id":"ph-1","session_id":"s-1","seq":7,
                 "lat":37.5,"lon":127.0,"accuracy_m":8.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(memberId, tsMs).getBytes(UTF_8);
    }
}
