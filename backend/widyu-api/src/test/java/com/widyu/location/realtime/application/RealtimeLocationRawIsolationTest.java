package com.widyu.location.realtime.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.SeniorLocation;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.raw.application.LocationFixService;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 실시간 처리의 기술 오류와 원본 저장을 나눠 다루는지 본다(LLD-0139). 원본 저장은 실시간 오류와 무관하게 시도하고,
 * ACK는 원본 저장과 실시간 처리가 모두 끝났을 때만 보낸다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("실시간 오류와 위치 원본 저장 분리")
class RealtimeLocationRawIsolationTest {

    @Mock private SeniorLocationRepository seniorLocationRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private ParentLocationRepository parentLocationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private ListOperations<String, Object> listOperations;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SafeZoneAlertService safeZoneAlertService;
    @Mock private LocationFixService locationFixService;
    @Mock private LocationAccessLogService locationAccessLogService;
    @Mock private MemberRepository memberRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Spy private MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 3_000);

    @InjectMocks
    private RealtimeLocationService realtimeLocationService;

    @Test
    @DisplayName("최신 위치를 읽다 Redis 장애가 나도 원본은 저장하고 ACK 없이 예외를 전달하며 방송하지 않는다")
    void 최신_위치_조회_Redis_장애에도_원본은_저장한다() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis());
        given(seniorLocationRepository.findById(1L)).willThrow(new RedisConnectionFailureException("down"));
        given(storeCall(payload)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> realtimeLocationService.updateAndBroadcast(payload, 1L))
                .isInstanceOf(RedisConnectionFailureException.class);
        then(locationFixService).should().store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("실시간 트랜잭션 안에서 체류 정보를 읽다 Redis 장애가 나도 원본은 저장하고 ACK 없이 예외를 전달한다")
    void 체류_정보_Redis_장애에도_원본은_저장한다() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis());
        stubSenior(1L);
        given(valueOperations.get("location:stay:1")).willThrow(new RedisConnectionFailureException("down"));
        given(storeCall(payload)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> realtimeLocationService.updateAndBroadcast(payload, 1L))
                .isInstanceOf(RedisConnectionFailureException.class);
        then(locationFixService).should().store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());
        then(seniorLocationRepository).should(never()).save(any(SeniorLocation.class));
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("원본을 저장한 뒤 실시간 처리만 실패한 위치를 다시 보내면 실시간 처리가 복구되고 DUPLICATE ACK를 돌려준다")
    void 원본_저장_뒤_실시간_실패는_재전송으로_복구된다() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis());
        stubSenior(1L);
        given(valueOperations.get("location:stay:1")).willReturn(null);
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        willThrow(new RedisConnectionFailureException("down"))
                .willReturn(null)
                .given(seniorLocationRepository).save(any(SeniorLocation.class));
        given(storeCall(payload)).willReturn(true, false);
        assertThatThrownBy(() -> realtimeLocationService.updateAndBroadcast(payload, 1L))
                .isInstanceOf(RedisConnectionFailureException.class);

        // when
        LocationAckResponse resent = realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        assertThat(resent.result()).isEqualTo(LocationAckResult.DUPLICATE);
        assertThat(resent.location().stayStartTime()).isNotNull();
        then(seniorLocationRepository).should(org.mockito.Mockito.times(2)).save(any(SeniorLocation.class));
        then(messagingTemplate).should()
                .convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
    }

    @Test
    @DisplayName("원본 저장이 실패하면 실시간 처리가 끝났어도 ACK 없이 예외를 전달한다")
    void 원본_저장이_실패하면_성공_ACK가_없다() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis() - 16 * 60_000L);
        willThrow(new IllegalStateException("DB 장애"))
                .given(locationFixService)
                .store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());

        // when & then
        assertThatThrownBy(() -> realtimeLocationService.updateAndBroadcast(payload, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("앱이 실어 보낸 회차가 남의 것이면 방송과 원본 저장 없이 REJECTED ACK를 돌려준다")
    void 남의_회차면_방송과_저장_없이_REJECTED() {
        // given
        byte[] payload = payloadWithRun(1L, System.currentTimeMillis(), "run-other");
        willThrow(new BusinessException(ErrorCode.RUN_NOT_FOUND))
                .given(locationFixService).verifyAttribution(eq(1L), any(LocationUpdateRequest.class));

        // when
        LocationAckResponse response = realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.REJECTED);
        then(seniorLocationRepository).should(never()).findById(any());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
        then(locationFixService).should(never())
                .store(any(), any(LocationUpdateRequest.class), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("다른 회원의 최근 위치는 최신 위치를 읽기 전에 REJECTED로 거절하고 방송과 원본 저장을 하지 않는다")
    void 다른_회원의_최근_위치는_REJECTED() {
        // given
        byte[] payload = payload(2L, System.currentTimeMillis());

        // when
        LocationAckResponse response = realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.REJECTED);
        then(seniorLocationRepository).should(never()).findById(any());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
        then(locationFixService).should(never())
                .store(any(), any(LocationUpdateRequest.class), any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("시니어가 아닌 회원의 위치는 15분 넘게 지나 실시간 처리를 건너뛰는 위치여도 원본을 저장하지 않고 REJECTED ACK를 돌려준다")
    void 시니어가_아니면_원본_없이_REJECTED() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis() - 16 * 60_000L);
        willThrow(new BusinessException(ErrorCode.BAD_REQUEST))
                .given(locationFixService)
                .store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());

        // when
        LocationAckResponse response = realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.REJECTED);
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("같은 원본이 동시에 와서 유일 키 충돌이 나도 이미 저장돼 있으면 DUPLICATE ACK를 돌려준다")
    void 원본_유일_키_경합은_DUPLICATE() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis() - 16 * 60_000L);
        willThrow(new DataIntegrityViolationException("uk_location_fix_seq"))
                .given(locationFixService)
                .store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());
        given(locationFixService.exists(any(LocationUpdateRequest.class))).willReturn(true);

        // when
        LocationAckResponse response = realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.DUPLICATE);
        assertThat(response.seq()).isEqualTo(7L);
    }

    @Test
    @DisplayName("유일 키 충돌인데 같은 원본이 없으면 다른 무결성 오류라 ACK 없이 예외를 전달한다")
    void 원본이_없는_무결성_오류는_예외() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis() - 16 * 60_000L);
        willThrow(new DataIntegrityViolationException("other"))
                .given(locationFixService)
                .store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());
        given(locationFixService.exists(any(LocationUpdateRequest.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> realtimeLocationService.updateAndBroadcast(payload, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 회원 위치가 몰려 실시간 차례를 얻지 못하면 원본만 저장하고 ACK를 보내지 않는다")
    void 실시간_대기_초과면_원본만_저장하고_ACK가_없다() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis());
        willThrow(new LocationSequenceBusyException()).given(sequencer).run(eq(1L), any(), any(), any());
        given(storeCall(payload)).willReturn(true);

        // when
        LocationAckResponse response = realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        assertThat(response).isNull();
        then(locationFixService).should().store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("같은 회원의 더 늦게 잰 위치가 처리 중이라 밀린 위치는 실시간 처리 없이 원본 저장 결과로 ACK를 돌려준다")
    void 더_최근_위치에_밀린_위치는_원본_저장_결과로_ACK한다() {
        // given
        byte[] payload = payload(1L, System.currentTimeMillis());
        willAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(3)).get())
                .given(sequencer).run(eq(1L), any(), any(), any());
        given(storeCall(payload)).willReturn(true);

        // when
        LocationAckResponse response = realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.STORED);
        assertThat(response.seq()).isEqualTo(7L);
        then(seniorLocationRepository).should(never()).findById(any());
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("미래로 잰 위치는 서버 수신 시각으로 한 번 자른 같은 값으로 순서를 판정하고 최신 위치에 저장한다")
    void 미래로_잰_위치는_한_번_자른_같은_시각으로_순서_판정과_저장을_한다() {
        // given
        long oneDayLater = System.currentTimeMillis() + 86_400_000L;
        byte[] payload = payload(1L, oneDayLater);
        willAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(2)).get())
                .given(sequencer).run(eq(1L), any(), any(), any());
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.empty());
        stubSenior(1L);
        given(valueOperations.get("location:stay:1")).willReturn(null);
        given(storeCall(payload)).willReturn(true);

        // when
        realtimeLocationService.updateAndBroadcast(payload, 1L);

        // then
        org.mockito.ArgumentCaptor<Long> orderAt = org.mockito.ArgumentCaptor.forClass(Long.class);
        then(sequencer).should().run(eq(1L), orderAt.capture(), any(), any());
        org.mockito.ArgumentCaptor<SeniorLocation> saved = org.mockito.ArgumentCaptor.forClass(SeniorLocation.class);
        then(seniorLocationRepository).should().save(saved.capture());
        assertThat(orderAt.getValue()).isLessThanOrEqualTo(System.currentTimeMillis());
        assertThat(saved.getValue().getMeasuredAtMs()).isEqualTo(orderAt.getValue());
    }

    private boolean storeCall(byte[] payload) {
        return locationFixService.store(eq(1L), any(LocationUpdateRequest.class), eq(payload), anyLong(), anyLong());
    }

    private void stubSenior(Long memberId) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ReflectionTestUtils.setField(member, "id", memberId);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        given(seniorProfileRepository.findByMemberId(memberId)).willReturn(Optional.of(seniorProfile));
        lenient().when(parentLocationRepository.findAllByMember(member)).thenReturn(List.of());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    private static byte[] payload(Long memberId, long tsMs) {
        return """
                {"v":2,"memberId":%d,"device_id":"ph-1","session_id":"s-1","seq":7,
                 "lat":37.5,"lon":127.0,"accuracy_m":8.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(memberId, tsMs).getBytes(UTF_8);
    }

    private static byte[] payloadWithRun(Long memberId, long tsMs, String runId) {
        return """
                {"v":2,"memberId":%d,"device_id":"ph-1","session_id":"s-1","seq":7,"run_id":"%s",
                 "lat":37.5,"lon":127.0,"accuracy_m":8.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                .formatted(memberId, runId, tsMs).getBytes(UTF_8);
    }
}
