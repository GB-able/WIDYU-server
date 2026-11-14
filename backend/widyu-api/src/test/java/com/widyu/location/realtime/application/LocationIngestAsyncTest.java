package com.widyu.location.realtime.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.widyu.global.websocket.WebSocketWorkDispatcher;
import com.widyu.location.SeniorLocation;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationSource;
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
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;
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
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 비동기 수신 경로(submit)가 ACK 계약을 지키는지 본다(LLD-0144). 우편함은 실제 구현이고 실행기만 손으로 돌린다.
 * 응답은 모두 원래 발신 세션으로 간다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("위치 비동기 수신 응답 계약")
class LocationIngestAsyncTest {

    private static final Principal USER = () -> "1";
    private static final String SESSION = "session-a";
    private static final String ACK = "/queue/location/ack";

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
    @Mock private WebSocketWorkDispatcher workDispatcher;
    @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Spy private MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 3_000);
    @Spy private FakeSeniorStayStateService stayStateService = new FakeSeniorStayStateService();

    @InjectMocks private LocationJudgeService judgeService;
    @InjectMocks private LocationProjectionService projectionService;

    private final ManualExecutor executor = new ManualExecutor();

    private LocationIngestService ingestService(MemberLocationMailbox mailbox) {
        return LocationServiceFixture.ingest(validator, sequencer, judgeService, projectionService, transactionManager,
                mailbox, workDispatcher);
    }

    @Test
    @DisplayName("입력 오류는 수신 스레드에서 바로 REJECTED를 돌려주고 우편함에 넣지 않는다")
    void 입력_오류는_바로_REJECTED() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        byte[] payload = payload(1L, 7, System.currentTimeMillis()).replace("\"move\"", "\"walk\"").getBytes(UTF_8);

        // when
        LocationAckResponse ack = ingestService(mailbox).submit(payload, 1L, USER, SESSION);

        // then
        assertThat(ack.result()).isEqualTo(LocationAckResult.REJECTED);
        assertThat(mailbox.queuedJobs()).isZero();
    }

    @Test
    @DisplayName("정상 위치는 수신 스레드에서 처리하지 않고, 위치 실행기가 반영한 뒤 원래 발신 세션에 APPLIED를 보낸다")
    void 정상_위치는_실행기에서_반영하고_발신_세션에_APPLIED() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        stubAppliedPath(stubSenior(1L));
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());

        // when
        LocationAckResponse immediate = ingestService(mailbox).submit(
                payload(1L, 7, System.currentTimeMillis()).getBytes(UTF_8), 1L, USER, SESSION);
        boolean savedBeforeWorker = savedAny();
        executor.runAll();

        // then
        assertThat(immediate).isNull();
        assertThat(savedBeforeWorker).isFalse();
        LocationAckResponse ack = sentAck();
        assertThat(ack.result()).isEqualTo(LocationAckResult.APPLIED);
        assertThat(ack.seq()).isEqualTo(7L);
        assertThat(mailbox.trackedMembers()).isZero();
    }

    @Test
    @DisplayName("더 늦게 잰 위치에 밀린 위치는 그 위치가 반영된 뒤에 요청 좌표로 SKIPPED를 받는다")
    void 밀린_위치는_반영_뒤에_SKIPPED() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        stubAppliedPath(stubSenior(1L));
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        long now = System.currentTimeMillis();
        LocationIngestService ingest = ingestService(mailbox);
        ingest.submit(payload(1L, 1, now - 2_000).getBytes(UTF_8), 1L, USER, SESSION);
        ingest.submit(payload(1L, 2, now - 1_000).getBytes(UTF_8), 1L, USER, SESSION);

        // when
        executor.runAll();

        // then
        List<LocationAckResponse> acks = sentAcks(2);
        assertThat(acks).extracting(LocationAckResponse::seq).containsExactly(2L, 1L);
        assertThat(acks).extracting(LocationAckResponse::result)
                .containsExactly(LocationAckResult.APPLIED, LocationAckResult.SKIPPED);
        assertThat(acks.get(1).location().locationSource()).isEqualTo(LocationSource.REQUEST);
        then(seniorLocationRepository).should().save(any(SeniorLocation.class));
    }

    @Test
    @DisplayName("시니어가 아닌 회원의 밀린 위치는 대신한 위치와 같이 REJECTED로 끝난다")
    void 시니어가_아니면_밀린_위치도_REJECTED() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        long now = System.currentTimeMillis();
        LocationIngestService ingest = ingestService(mailbox);
        ingest.submit(payload(1L, 1, now - 2_000).getBytes(UTF_8), 1L, USER, SESSION);
        ingest.submit(payload(1L, 2, now - 1_000).getBytes(UTF_8), 1L, USER, SESSION);

        // when
        executor.runAll();

        // then
        assertThat(sentAcks(2)).extracting(LocationAckResponse::result)
                .containsExactly(LocationAckResult.REJECTED, LocationAckResult.REJECTED);
    }

    @Test
    @DisplayName("판정이 기술 오류로 실패하면 성공 ACK 없이 발신 세션에 오류를 보내고, 밀린 위치는 과부하로 끝낸다")
    void 기술_오류면_ACK_없이_오류와_과부하() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        stubSenior(1L);
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        willThrow(new QueryTimeoutException("DB timeout")).given(stayStateService).find(1L);
        long now = System.currentTimeMillis();
        LocationIngestService ingest = ingestService(mailbox);
        ingest.submit(payload(1L, 1, now - 2_000).getBytes(UTF_8), 1L, USER, SESSION);
        ingest.submit(payload(1L, 2, now - 1_000).getBytes(UTF_8), 1L, USER, SESSION);

        // when
        executor.runAll();

        // then
        then(workDispatcher).should(never()).sendToSession(anyString(), anyString(), eq(ACK), any());
        then(workDispatcher).should().sendError(any(QueryTimeoutException.class), anyString(), anyString(), eq(USER),
                eq(SESSION));
        then(workDispatcher).should().sendOverloaded(anyString(), eq(USER), eq(SESSION));
        assertThat(mailbox.trackedMembers()).isZero();
        assertThat(mailbox.queuedJobs()).isZero();
    }

    @Test
    @DisplayName("작업 스레드에서 회원 순서 잠금이 대기 초과면 성공 ACK 없이 발신 세션에 과부하를 알린다")
    void 순서_잠금_대기_초과면_과부하를_알린다() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        willThrow(new LocationSequenceBusyException()).given(sequencer)
                .run(eq(1L), any(), any(), any());

        // when
        ingestService(mailbox).submit(payload(1L, 7, System.currentTimeMillis()).getBytes(UTF_8), 1L, USER, SESSION);
        executor.runAll();

        // then
        then(workDispatcher).should().sendOverloaded(anyString(), eq(USER), eq(SESSION));
        then(workDispatcher).should(never()).sendToSession(anyString(), anyString(), eq(ACK), any());
        assertThat(mailbox.trackedMembers()).isZero();
    }

    @Test
    @DisplayName("우편함 자리가 없으면 수신 스레드에서 실행하지 않고 성공 ACK 없이 과부하만 알린다")
    void 자리가_없으면_과부하만_알린다() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 1);
        LocationIngestService ingest = ingestService(mailbox);
        ingest.submit(payload(1L, 1, System.currentTimeMillis()).getBytes(UTF_8), 1L, USER, SESSION);

        // when
        ingest.submit(payload(2L, 2, System.currentTimeMillis()).getBytes(UTF_8), 2L, () -> "2", "session-b");

        // then
        then(workDispatcher).should().sendOverloaded(anyString(), any(Principal.class), eq("session-b"));
        then(workDispatcher).should(never()).sendToSession(anyString(), anyString(), eq(ACK), any());
        then(seniorLocationRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("구형 위치도 우편함을 거쳐 실행기에서 반영하고 식별자 없는 ACK를 보낸다")
    void 구형_위치도_실행기에서_반영한다() {
        // given
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        stubAppliedPath(stubSenior(1L));

        // when
        LocationAckResponse immediate = ingestService(mailbox).submit(
                "{\"memberId\":1,\"latitude\":37.5,\"longitude\":127.0}".getBytes(UTF_8), 1L, USER, SESSION);
        executor.runAll();

        // then
        assertThat(immediate).isNull();
        LocationAckResponse ack = sentAck();
        assertThat(ack.result()).isNull();
        assertThat(ack.location().locationSource()).isEqualTo(LocationSource.LATEST);
    }

    private boolean savedAny() {
        return !org.mockito.Mockito.mockingDetails(seniorLocationRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .toList().isEmpty();
    }

    private LocationAckResponse sentAck() {
        return sentAcks(1).get(0);
    }

    private List<LocationAckResponse> sentAcks(int count) {
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        then(workDispatcher).should(org.mockito.Mockito.times(count))
                .sendToSession(eq("1"), eq(SESSION), eq(ACK), payloads.capture());
        return payloads.getAllValues().stream().map(LocationAckResponse.class::cast).toList();
    }

    private Member stubSenior(Long memberId) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ReflectionTestUtils.setField(member, "id", memberId);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        given(seniorProfileRepository.findByMemberId(memberId)).willReturn(Optional.of(seniorProfile));
        return member;
    }

    private void stubAppliedPath(Member member) {
        given(parentLocationRepository.findAllByMember(member)).willReturn(List.of());
        given(redisTemplate.opsForList()).willReturn(listOperations);
    }

    private static String payload(Long memberId, long seq, long tsMs) {
        return """
                {"v":2,"memberId":%d,"device_id":"ph-1","session_id":"s-1","seq":%d,
                 "lat":37.5,"lon":127.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(memberId, seq, tsMs);
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }
}
