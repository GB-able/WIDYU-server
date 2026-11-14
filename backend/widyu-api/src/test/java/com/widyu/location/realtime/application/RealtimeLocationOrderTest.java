package com.widyu.location.realtime.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;

import com.widyu.location.SeniorLocation;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 같은 회원의 위치가 동시에 처리될 때 최신 위치, 방송 순서가 측정 시각을 따르는지 본다(LLD-0139).
 * 최신 시각 비교와 최신 위치 저장 사이를 테스트가 벌려, 수정 전 코드에서 옛 위치가 최신 위치를 덮는 경합을 재현한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("실시간 위치 처리 순서")
class RealtimeLocationOrderTest {

    private static final long WAIT_MS = 300;

    @Mock private SeniorLocationRepository seniorLocationRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock(strictness = Mock.Strictness.LENIENT) private SeniorProfileRepository seniorProfileRepository;
    @Mock(strictness = Mock.Strictness.LENIENT) private ParentLocationRepository parentLocationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock(strictness = Mock.Strictness.LENIENT) private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
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
    @InjectMocks private LocationQueryService queryService;
    @InjectMocks private LocationMaintenanceService maintenanceService;

    private LocationIngestService ingestService() {
        return LocationServiceFixture.ingest(validator, sequencer, judgeService, projectionService,
                transactionManager);
    }

    @Test
    @DisplayName("같은 회원의 새 위치와 옛 위치가 동시에 처리돼도 최신 위치와 마지막 방송에 나중에 잰 위치를 반영한다")
    void 같은_회원의_위치가_동시에_와도_옛_위치가_최신_위치를_덮지_않는다() throws Exception {
        // given
        long now = System.currentTimeMillis();
        long newerTs = now;
        long olderTs = now - 5_000L;
        Map<Long, SeniorLocation> latest = new ConcurrentHashMap<>();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch newerSaved = new CountDownLatch(1);
        stubSenior(1L);
        // 두 요청이 최신 위치를 함께 읽게 벌린다. 순서를 지키는 구현에서는 두 번째 읽기가 오지 않아 시간 초과로 넘어간다.
        willAnswer(invocation -> {
            Optional<SeniorLocation> seen = Optional.ofNullable(latest.get(1L));
            bothRead.countDown();
            bothRead.await(WAIT_MS, TimeUnit.MILLISECONDS);
            return seen;
        }).given(seniorLocationRepository).findById(1L);
        // 옛 위치의 최신 위치 저장을 새 위치 저장 뒤로 늦춘다.
        willAnswer(invocation -> {
            SeniorLocation location = invocation.getArgument(0);
            if (location.getMeasuredAtMs() == olderTs) {
                newerSaved.await(WAIT_MS, TimeUnit.MILLISECONDS);
            }
            latest.put(1L, location);
            if (location.getMeasuredAtMs() == newerTs) {
                newerSaved.countDown();
            }
            return location;
        }).given(seniorLocationRepository).save(any(SeniorLocation.class));

        // when
        LocationIngestService ingestService = ingestService();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> newer = pool.submit(() ->
                    ingestService.receive(payload(1L, 1, 37.60, newerTs), 1L));
            Future<?> older = pool.submit(() ->
                    ingestService.receive(payload(1L, 2, 37.50, olderTs), 1L));
            newer.get(5, TimeUnit.SECONDS);
            older.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // then
        assertThat(latest.get(1L).getMeasuredAtMs()).isEqualTo(newerTs);
        ArgumentCaptor<LocationUpdateResponse> broadcasts = ArgumentCaptor.forClass(LocationUpdateResponse.class);
        then(messagingTemplate).should(atLeastOnce())
                .convertAndSend(eq("/topic/location/senior/1"), broadcasts.capture());
        List<LocationUpdateResponse> sent = broadcasts.getAllValues();
        assertThat(sent.get(sent.size() - 1).latitude()).isEqualTo(37.60);
    }

    @Test
    @DisplayName("한 회원의 위치 처리가 멈춰 있어도 다른 회원의 위치는 기다리지 않고 처리한다")
    void 한_회원이_처리_중이어도_다른_회원은_기다리지_않는다() throws Exception {
        // given
        long now = System.currentTimeMillis();
        stubSenior(1L);
        stubSenior(2L);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        willAnswer(invocation -> {
            firstEntered.countDown();
            releaseFirst.await(5, TimeUnit.SECONDS);
            return Optional.empty();
        }).given(seniorLocationRepository).findById(1L);
        given(seniorLocationRepository.findById(2L)).willReturn(Optional.empty());

        // when
        LocationIngestService ingestService = ingestService();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> blocked = pool.submit(() ->
                    ingestService.receive(payload(1L, 1, 37.5, now), 1L));
            assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> other = pool.submit(() ->
                    ingestService.receive(payload(2L, 1, 37.5, now), 2L));
            other.get(2, TimeUnit.SECONDS);

            // then
            assertThat(blocked.isDone()).isFalse();
            then(messagingTemplate).should(timeout(1_000))
                    .convertAndSend(eq("/topic/location/senior/2"), any(LocationUpdateResponse.class));
            releaseFirst.countDown();
            blocked.get(2, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }

    private void stubSenior(Long memberId) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ReflectionTestUtils.setField(member, "id", memberId);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        given(seniorProfileRepository.findByMemberId(memberId)).willReturn(Optional.of(seniorProfile));
        given(parentLocationRepository.findAllByMember(member)).willReturn(List.of());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForList()).willReturn(listOperations);
    }

    private static byte[] payload(Long memberId, long seq, double latitude, long tsMs) {
        return """
                {"v":2,"memberId":%d,"device_id":"ph-1","session_id":"s-1","seq":%d,
                 "lat":%s,"lon":127.0,"accuracy_m":8.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(memberId, seq, latitude, tsMs).getBytes(UTF_8);
    }
}
