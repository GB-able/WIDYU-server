package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.widyu.global.config.RedisConfig;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.SeniorLocation;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationSource;
import com.widyu.location.stay.SeniorStayState;
import java.time.LocalDateTime;
import com.widyu.location.realtime.dto.LocationPoint;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.realtime.event.SeniorLocationUpdatedEvent;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("실시간 위치 수신, 판정, 반영, 조회, 유지관리 단위 테스트")
class RealtimeLocationServiceTest {

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
    @Mock private LocationAccessLogService locationAccessLogService;
    @Mock private MemberRepository memberRepository;
    @Mock private PlatformTransactionManager transactionManager;
    // 실제 제약(memberId @NotNull)을 그대로 태운다. 목이면 검증이 통째로 비어 버린다.
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
    @DisplayName("탈퇴 회원의 위치를 지우면 Redis 현재 위치, 이동 경로, 예전 체류 키와 MySQL 체류 상태를 모두 삭제한다")
    void 탈퇴_회원의_위치를_지우면_Redis_위치_키를_모두_삭제한다() {
        // when
        maintenanceService.deleteLocation(7L);

        // then
        then(seniorLocationRepository).should().deleteById(7L);
        then(redisTemplate).should().delete(java.util.List.of("location:trail:7", "location:stay:7"));
        then(stayStateService).should().delete(7L);
    }

    @Test
    @DisplayName("위치 TTL을 갱신할 때 활성 회원은 위치를 다시 저장하지 않고 만료 시간만 늘리며 활성이 아닌 회원의 위치는 지운다")
    void 위치_TTL_갱신_시_활성_회원만_갱신하고_나머지는_지운다() {
        // given
        SeniorLocation active = SeniorLocation.of(1L, 37.5, 127.0);
        SeniorLocation withdrawn = SeniorLocation.of(2L, 37.5, 127.0);
        given(seniorLocationRepository.findAll()).willReturn(java.util.List.of(active, withdrawn));
        given(memberRepository.findActiveIdsIn(java.util.List.of(1L, 2L))).willReturn(java.util.Set.of(1L));

        // when
        maintenanceService.refreshLocationTtl();

        // then
        then(redisTemplate).should().expire("senior_location:1", 300L, TimeUnit.SECONDS);
        then(redisTemplate).should(never()).expire("senior_location:2", 300L, TimeUnit.SECONDS);
        then(seniorLocationRepository).should(never()).save(any(SeniorLocation.class));
        then(seniorLocationRepository).should().deleteById(2L);
        then(redisTemplate).should().delete(java.util.List.of("location:trail:2", "location:stay:2"));
    }

    @Test
    @DisplayName("Redis 위치 삭제가 실패해도 MySQL 체류 상태는 지운다")
    void Redis_삭제가_실패해도_체류_상태는_지운다() {
        // given
        stayStateService.create(SeniorStayState.start(7L, 37.5, 127.0, LocalDateTime.now(), null, null));
        willThrow(new QueryTimeoutException("redis down")).given(seniorLocationRepository).deleteById(7L);

        // when & then
        assertThatThrownBy(() -> maintenanceService.deleteLocation(7L))
                .isInstanceOf(QueryTimeoutException.class);
        assertThat(stayStateService.states).doesNotContainKey(7L);
    }

    @Test
    @DisplayName("최신 위치가 이미 만료된 비활성 회원도 TTL 갱신 때 MySQL 체류 상태를 지운다")
    void 최신_위치가_없는_비활성_회원의_체류_상태도_지운다() {
        // given
        stayStateService.create(SeniorStayState.start(3L, 37.5, 127.0, LocalDateTime.now(), null, null));
        stayStateService.create(SeniorStayState.start(4L, 37.5, 127.0, LocalDateTime.now(), null, null));
        stayStateService.inactiveMemberIds.add(3L);
        given(seniorLocationRepository.findAll()).willReturn(java.util.List.of());

        // when
        maintenanceService.refreshLocationTtl();

        // then
        assertThat(stayStateService.states).containsOnlyKeys(4L);
        then(redisTemplate).should().delete(java.util.List.of("location:trail:3", "location:stay:3"));
    }

    // 실시간 반영 조건(LLD-0114)을 통과하도록 잰 시각을 지금으로 둔다.
    private static String rawFixPayload(long tsMs) {
        return """
            {"v":2,"memberId":1,"device_id":"ph-9c1","session_id":"s-2026","seq":5120,
             "lat":37.5551,"lon":126.9707,"accuracy_m":8.5,"speed_mps":0.9,"provider":"fused",
             "is_mock":false,"ts_ms":%d,"reason":"move"}""".formatted(tsMs);
    }

    @Test
    @DisplayName("인증 회원과 요청 회원이 다르면 FORBIDDEN 예외를 던지고 위치를 저장하지 않는다")
    void 인증_회원과_요청_회원이_다르면_예외가_발생한다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(2L, 37.5, 127.0, null);

        // when & then
        assertThatThrownBy(() -> ingestService().applyRealtime(request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("본인의 위치만 업데이트할 수 있습니다.");
        then(seniorLocationRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("존재하지 않는 시니어 위치 업데이트 시 BAD_REQUEST 예외를 던진다")
    void 존재하지_않는_시니어_위치_업데이트_시_예외가_발생한다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ingestService().applyRealtime(request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("존재하지 않는 시니어입니다.");
    }

    @Test
    @DisplayName("위치를 업데이트하면 최신 위치와 이동 경로를 저장하고 보호자 topic으로 브로드캐스트한다")
    void 위치를_업데이트하면_저장하고_브로드캐스트한다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        Member member = member(1L);
        SeniorProfile seniorProfile = seniorProfile(10L, member);

        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile));
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.leftPush(eq("location:trail:1"), any(LocationPoint.class))).willReturn(1L);
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());

        // when
        LocationUpdateResponse response = ingestService().applyRealtime(request, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.latitude()).isEqualTo(37.5);
        assertThat(response.longitude()).isEqualTo(127.0);
        then(seniorLocationRepository).should().save(any(SeniorLocation.class));
        then(eventPublisher).should().publishEvent(any(SeniorLocationUpdatedEvent.class));
        then(listOperations).should().leftPush(eq("location:trail:1"), any(LocationPoint.class));
        assertThat(stayStateService.states.get(1L).getLatitude()).isEqualTo(37.5);
        then(safeZoneAlertService).should()
                .handleSafeZoneTransition(any(SeniorStayState.class), eq(null), eq(null), any(LocalDateTime.class));
        then(messagingTemplate).should().convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
    }

    @Test
    @DisplayName("연결되지 않은 보호자가 마지막 위치 조회 시 FORBIDDEN 예외를 던진다")
    void 연결되지_않은_보호자_마지막_위치_조회_시_예외가_발생한다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> queryService.getLastLocation(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("해당 시니어의 위치를 조회할 권한이 없습니다.");
    }

    @Test
    @DisplayName("최근 위치와 체류 정보가 모두 없으면 NOT_FOUND 예외를 던진다")
    void 최근_위치와_체류_정보가_모두_없으면_예외가_발생한다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        given(seniorLocationRepository.findBySeniorId(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> queryService.getLastLocation(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND)
                .hasMessageContaining("최근 위치 정보가 없습니다.");
    }

    @Test
    @DisplayName("집 밖의 체류 위치이면 외출 중으로 반환한다")
    void 집_밖의_체류위치이면_외출중으로_반환한다() {
        // given
        stayStateService.states.put(1L, SeniorStayState.start(1L, 37.5, 127.0, LocalDateTime.now(), null, null));

        // when
        Boolean isOuting = queryService.getOutingStatus(1L);

        // then
        assertThat(isOuting).isTrue();
    }

    @Test
    @DisplayName("집 체류 상태에서 30m 넘게 움직이면 행을 잠가 다시 읽고 이전 위치 타입을 HOME으로 넘긴다")
    void 집_체류에서_밖으로_나가면_잠근_뒤_이전_위치_타입을_HOME으로_넘긴다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.6, 127.0, null);
        Member member = member(1L);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member)));
        given(redisTemplate.opsForList()).willReturn(listOperations);
        stayStateService.states.put(1L, SeniorStayState.start(1L, 37.5, 127.0, LocalDateTime.now(), "HOME", "집"));
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());

        // when
        ingestService().applyRealtime(request, 1L);

        // then
        then(stayStateService).should().lockForChange(1L);
        then(safeZoneAlertService).should()
                .handleSafeZoneTransition(any(SeniorStayState.class), eq("HOME"), eq(null), any(LocalDateTime.class));
        assertThat(stayStateService.states.get(1L).getLatitude()).isEqualTo(37.6);
    }

    @Test
    @DisplayName("최신 위치가 없으면 체류 상태 좌표로 답하되 출처를 STAY로, 측정 시각을 모름으로 표시한다")
    void 최신_위치가_없으면_체류_좌표로_답하고_측정_시각은_모름이다() {
        // given
        LocalDateTime stayStartedAt = LocalDateTime.now().minusHours(3);
        stayStateService.states.put(2L, SeniorStayState.start(2L, 37.5, 127.0, stayStartedAt, "HOME", "집"));
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        given(seniorLocationRepository.findBySeniorId(2L)).willReturn(Optional.empty());

        // when
        LocationUpdateResponse response = queryService.getLastLocation(2L, 1L);

        // then
        assertThat(response.stayStartTime()).isEqualTo(stayStartedAt);
        assertThat(response.locationType()).isEqualTo("HOME");
        assertThat(response.locationName()).isEqualTo("집");
        assertThat(response.latitude()).isEqualTo(37.5);
        assertThat(response.locationSource()).isEqualTo(LocationSource.STAY);
        assertThat(response.measuredAtMs()).isNull();
        assertThat(response.ageSeconds()).isNull();
    }

    @Test
    @DisplayName("오래된 최신 위치를 조회하면 TTL이 남아 있어도 측정 시각과 경과 초로 오래됐음을 알리고 updatedAt과 구분한다")
    void 오래된_최신_위치는_측정_시각과_경과로_알린다() {
        // given
        long tenHoursAgo = System.currentTimeMillis() - 10 * 3_600_000L;
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        given(seniorLocationRepository.findBySeniorId(2L)).willReturn(Optional.of(
                SeniorLocation.of(2L, 37.5, 127.0, tenHoursAgo, tenHoursAgo, tenHoursAgo + 2_000L)));

        // when
        LocationUpdateResponse response = queryService.getLastLocation(2L, 1L);

        // then
        assertThat(response.locationSource()).isEqualTo(LocationSource.LATEST);
        assertThat(response.measuredAtMs()).isEqualTo(tenHoursAgo);
        assertThat(response.receivedAtMs()).isEqualTo(tenHoursAgo + 2_000L);
        assertThat(response.ageSeconds()).isBetween(35_999L, 36_001L);
        assertThat(response.stayStartTime()).isNull();
        assertThat(response.stayDurationMinutes()).isNull();
        assertThat(response.updatedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    @DisplayName("이 필드가 생기기 전 최신 위치는 순서 시각을 측정 시각으로 보여 주고, 시각이 전혀 없는 옛 형식은 측정 시각을 비운다")
    void 예전_최신_위치는_순서_시각을_쓰고_시각이_없으면_비운다() {
        // given
        long earlier = System.currentTimeMillis() - 60_000L;
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        given(seniorLocationRepository.findBySeniorId(2L))
                .willReturn(Optional.of(SeniorLocation.of(2L, 37.5, 127.0, earlier, null, null)))
                .willReturn(Optional.of(SeniorLocation.of(2L, 37.5, 127.0)));

        // when
        LocationUpdateResponse beforeField = queryService.getLastLocation(2L, 1L);
        LocationUpdateResponse legacy = queryService.getLastLocation(2L, 1L);

        // then
        assertThat(beforeField.measuredAtMs()).isEqualTo(earlier);
        assertThat(beforeField.receivedAtMs()).isNull();
        assertThat(legacy.measuredAtMs()).isNull();
        assertThat(legacy.ageSeconds()).isNull();
        assertThat(legacy.locationSource()).isEqualTo(LocationSource.LATEST);
    }

    @Test
    @DisplayName("미래로 잰 위치를 반영하면 방송 응답에는 앱이 보낸 측정 시각을, 최신 위치에는 그 값과 수신 시각을 함께 남기고 경과는 0이다")
    void 미래로_잰_위치는_원래_측정_시각과_수신_시각을_구분해_남긴다() {
        // given
        long oneHourLater = System.currentTimeMillis() + 3_600_000L;
        Member member = member(1L);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member)));
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());

        // when
        LocationUpdateResponse response =
                ingestService().applyRealtime(LocationUpdateRequest.of(1L, 37.5, 127.0, null), 1L);
        ingestService().receive(rawFixPayload(oneHourLater).getBytes(UTF_8), 1L);

        // then
        assertThat(response.measuredAtMs()).isNull();
        ArgumentCaptor<SeniorLocation> saved = ArgumentCaptor.forClass(SeniorLocation.class);
        then(seniorLocationRepository).should(times(2)).save(saved.capture());
        SeniorLocation future = saved.getAllValues().get(1);
        assertThat(future.getDeviceMeasuredAtMs()).isEqualTo(oneHourLater);
        assertThat(future.getMeasuredAtMs()).isLessThan(oneHourLater);
        assertThat(future.getReceivedAtMs()).isEqualTo(future.getMeasuredAtMs());
        ArgumentCaptor<LocationUpdateResponse> broadcast = ArgumentCaptor.forClass(LocationUpdateResponse.class);
        then(messagingTemplate).should(times(2)).convertAndSend(eq("/topic/location/senior/1"), broadcast.capture());
        assertThat(broadcast.getAllValues().get(1).measuredAtMs()).isEqualTo(oneHourLater);
        assertThat(broadcast.getAllValues().get(1).ageSeconds()).isZero();
    }

    @Test
    @DisplayName("연결되지 않은 보호자가 이동 경로 조회 시 FORBIDDEN 예외를 던진다")
    void 연결되지_않은_보호자_이동_경로_조회_시_예외가_발생한다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> queryService.getLocationTrail(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("해당 시니어의 위치를 조회할 권한이 없습니다.");
    }

    @Test
    @DisplayName("v2 위치 페이로드를 보내면 기존 실시간 경로를 돌리고 APPLIED ACK를 돌려준다")
    void v2_위치_페이로드를_보내면_실시간_경로를_돌리고_APPLIED를_돌려준다() {
        // given
        byte[] payload = rawFixPayload(System.currentTimeMillis()).getBytes(UTF_8);
        Member member = member(1L);
        SeniorProfile seniorProfile = seniorProfile(10L, member);

        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile));
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.leftPush(eq("location:trail:1"), any(LocationPoint.class))).willReturn(1L);
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());

        // when
        LocationAckResponse response = ingestService().receive(payload, 1L);

        // then
        assertThat(response.location().memberId()).isEqualTo(1L);
        assertThat(response.location().latitude()).isEqualTo(37.5551);
        assertThat(response.location().longitude()).isEqualTo(126.9707);
        assertThat(response.deviceId()).isEqualTo("ph-9c1");
        assertThat(response.sessionId()).isEqualTo("s-2026");
        assertThat(response.seq()).isEqualTo(5120L);
        assertThat(response.result()).isEqualTo(LocationAckResult.APPLIED);
        then(seniorLocationRepository).should().save(any(SeniorLocation.class));
        then(listOperations).should().leftPush(eq("location:trail:1"), any(LocationPoint.class));
        then(messagingTemplate).should()
                .convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
    }

    @Test
    @DisplayName("기존 4필드 페이로드를 보내면 기존대로 브로드캐스트하고 식별자와 결과 없는 ACK를 돌려준다")
    void 기존_4필드_페이로드를_보내면_식별자_없는_ACK를_돌려준다() {
        // given
        byte[] payload = "{\"memberId\":1,\"latitude\":37.5,\"longitude\":127.0}".getBytes(UTF_8);
        Member member = member(1L);
        SeniorProfile seniorProfile = seniorProfile(10L, member);

        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile));
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.leftPush(eq("location:trail:1"), any(LocationPoint.class))).willReturn(1L);
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());

        // when
        LocationAckResponse response = ingestService().receive(payload, 1L);

        // then
        assertThat(response.location().latitude()).isEqualTo(37.5);
        assertThat(response.deviceId()).isNull();
        assertThat(response.seq()).isNull();
        assertThat(response.result()).isNull();
        then(messagingTemplate).should()
                .convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
    }

    @Test
    @DisplayName("15분보다 오래 전에 잰 위치는 실시간에 반영하지 않고 받은 좌표로 SKIPPED ACK를 돌려준다")
    void 오래전에_잰_위치는_실시간에_반영하지_않고_SKIPPED() {
        // given
        long sixteenMinutesAgo = System.currentTimeMillis() - 16 * 60_000L;
        byte[] payload = rawFixPayload(sixteenMinutesAgo).getBytes(UTF_8);
        given(seniorProfileRepository.existsByMemberId(1L)).willReturn(true);

        // when
        LocationAckResponse response = ingestService().receive(payload, 1L);

        // then
        assertThat(response.location().latitude()).isEqualTo(37.5551);
        assertThat(response.location().stayStartTime()).isNull();
        assertThat(response.result()).isEqualTo(LocationAckResult.SKIPPED);
        then(seniorLocationRepository).should(never()).save(any(SeniorLocation.class));
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
        then(eventPublisher).should(never()).publishEvent(any());
        then(seniorProfileRepository).should(never()).findByMemberId(any()); // 판정 트랜잭션을 열지 않았다
    }

    @Test
    @DisplayName("지금 최신 위치보다 먼저 잰 위치는 실시간에 반영하지 않아 최신 위치를 되돌리지 않는다")
    void 최신_위치보다_먼저_잰_위치는_실시간에_반영하지_않는다() {
        // given
        long now = System.currentTimeMillis();
        given(seniorLocationRepository.findById(1L))
                .willReturn(Optional.of(SeniorLocation.of(1L, 37.6, 127.0, now)));
        byte[] payload = rawFixPayload(now - 60_000L).getBytes(UTF_8);
        given(seniorProfileRepository.existsByMemberId(1L)).willReturn(true);

        // when
        LocationAckResponse response = ingestService().receive(payload, 1L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.SKIPPED);
        then(seniorLocationRepository).should(never()).save(any(SeniorLocation.class));
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("기기 시계가 앞서 미래로 잰 위치도 반영하되 최신 위치 기준 시각은 지금으로 잘라 다음 정상 위치를 막지 않는다")
    void 미래로_잰_위치가_와도_다음_정상_위치를_막지_않는다() {
        // given
        Member member = member(1L);
        SeniorProfile seniorProfile = seniorProfile(10L, member);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile));
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.leftPush(eq("location:trail:1"), any(LocationPoint.class))).willReturn(1L);
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());
        long oneDayLater = System.currentTimeMillis() + 86_400_000L;
        ArgumentCaptor<SeniorLocation> saved = ArgumentCaptor.forClass(SeniorLocation.class);
        ingestService().receive(rawFixPayload(oneDayLater).getBytes(UTF_8), 1L);
        then(seniorLocationRepository).should().save(saved.capture());
        given(seniorLocationRepository.findById(1L)).willReturn(Optional.of(saved.getValue()));

        // when
        ingestService().receive(
                rawFixPayload(System.currentTimeMillis() + 1_000L).getBytes(UTF_8), 1L);

        // then
        assertThat(saved.getValue().getMeasuredAtMs()).isLessThan(oneDayLater);
        then(seniorLocationRepository).should(times(2)).save(any(SeniorLocation.class));
        then(messagingTemplate).should(times(2))
                .convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
    }

    @Test
    @DisplayName("다른 회원의 오래된 위치는 실시간 반영 생략 전에 REJECTED로 거절한다")
    void 다른_회원의_오래된_위치는_REJECTED로_거절한다() {
        // given
        byte[] payload = rawFixPayload(System.currentTimeMillis() - 16 * 60_000L).getBytes(UTF_8);

        // when
        LocationAckResponse response = ingestService().receive(payload, 2L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.REJECTED);
        assertThat(response.seq()).isEqualTo(5120L);
    }

    @Test
    @DisplayName("v2 위치가 입력 검증에 실패하면 방송 없이 REJECTED ACK를 돌려준다")
    void v2_위치가_입력_검증에_실패하면_REJECTED_ACK를_돌려준다() {
        // given
        byte[] payload = rawFixPayload(System.currentTimeMillis()).replace("\"move\"", "\"walk\"").getBytes(UTF_8);

        // when
        LocationAckResponse response = ingestService().receive(payload, 1L);

        // then
        assertThat(response.result()).isEqualTo(LocationAckResult.REJECTED);
        assertThat(response.location()).isNull();
        assertThat(response.deviceId()).isEqualTo("ph-9c1");
        assertThat(response.seq()).isEqualTo(5120L);
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("구형 위치가 검증에 실패하면 ACK 대신 LOCATION_FIX_INVALID 예외가 발생한다")
    void 구형_위치가_검증에_실패하면_예외가_발생한다() {
        // given
        byte[] payload = "{\"memberId\":1,\"latitude\":37.5}".getBytes(UTF_8);

        // when & then
        assertThatThrownBy(() -> ingestService().receive(payload, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_FIX_INVALID);
    }

    @Test
    @DisplayName("본문을 읽을 수 없으면 LOCATION_FIX_INVALID 예외가 발생한다")
    void 본문을_읽을_수_없으면_예외가_발생한다() {
        // given
        byte[] payload = "not-json".getBytes(UTF_8);

        // when & then
        assertThatThrownBy(() -> ingestService().receive(payload, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_FIX_INVALID);
    }

    @Test
    @DisplayName("보호자가 마지막 위치를 조회하면 권한 검증 뒤 REST_LAST 열람 기록을 남긴다")
    void 마지막_위치_조회는_REST_LAST_기록을_남긴다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        stayStateService.states.put(2L, SeniorStayState.start(2L, 37.5, 127.0, LocalDateTime.now(), null, null));
        given(seniorLocationRepository.findBySeniorId(2L)).willReturn(Optional.empty());

        // when
        LocationUpdateResponse response = queryService.getLastLocation(2L, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(2L);
        then(locationAccessLogService).should().record(1L, 2L, LocationAccessPath.REST_LAST);
    }

    @Test
    @DisplayName("권한이 없어 위치 조회가 막히면 열람 기록을 남기지 않는다")
    void 권한이_없으면_열람_기록을_남기지_않는다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> queryService.getLastLocation(2L, 1L))
                .isInstanceOf(BusinessException.class);
        then(locationAccessLogService).should(never())
                .record(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(LocationAccessPath.class));
    }

    @Test
    @DisplayName("열람 기록이 예외를 던져도 위치 응답은 그대로 돌려준다")
    void 열람_기록이_실패해도_위치_응답을_돌려준다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        stayStateService.states.put(2L, SeniorStayState.start(2L, 37.5, 127.0, LocalDateTime.now(), null, null));
        given(seniorLocationRepository.findBySeniorId(2L)).willReturn(Optional.empty());
        willThrow(new IllegalStateException("DB 장애"))
                .given(locationAccessLogService).record(1L, 2L, LocationAccessPath.REST_LAST);

        // when
        LocationUpdateResponse response = queryService.getLastLocation(2L, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.latitude()).isEqualTo(37.5);
    }

    @Test
    @DisplayName("보호자가 이동 경로를 조회하면 REST_TRAIL 열람 기록을 남긴다")
    void 이동_경로_조회는_REST_TRAIL_기록을_남긴다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("location:trail:2", 0, 899)).willReturn(java.util.List.of());

        // when
        queryService.getLocationTrail(2L, 1L);

        // then
        then(locationAccessLogService).should().record(1L, 2L, LocationAccessPath.REST_TRAIL);
    }

    @Test
    @DisplayName("위치를 업데이트하면 이동 경로를 최근 900점으로 자르고 TTL을 다시 건다")
    void 위치를_업데이트하면_이동_경로를_최근_900점으로_자른다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        Member member = member(1L);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member)));
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.leftPush(eq("location:trail:1"), any(LocationPoint.class))).willReturn(901L);
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());

        // when
        ingestService().applyRealtime(request, 1L);

        // then
        then(listOperations).should().trim("location:trail:1", 0, 899);
        then(redisTemplate).should().expire("location:trail:1", 900L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("이동 경로를 조회하면 최근 900점까지만 읽어 오래된 순으로 돌려준다")
    void 이동_경로를_조회하면_최근_900점까지만_읽어_오래된_순으로_돌려준다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        long now = System.currentTimeMillis();
        LocationPoint newer = LocationPoint.of(37.51, 127.0, now);
        LocationPoint older = LocationPoint.of(37.50, 127.0, now - 60_000L);
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("location:trail:2", 0, 899)).willReturn(java.util.List.of(newer, older));

        // when
        LocationTrailResponse response = queryService.getLocationTrail(2L, 1L);

        // then
        assertThat(response.trail()).containsExactly(older, newer);
    }

    @Test
    @DisplayName("이동 경로는 측정 시각 기준 최근 15분 점만 돌려주고, 측정 시각이 없는 예전 점은 저장 시각으로 거른다")
    void 이동_경로는_측정_시각_기준_15분만_돌려준다() {
        // given
        long now = System.currentTimeMillis();
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        LocationPoint recent = LocationPoint.of(37.52, 127.0, now - 60_000L);
        LocationPoint stale = LocationPoint.of(37.51, 127.0, now - 16 * 60_000L);
        LocationPoint legacyRecent = new LocationPoint(37.50, 127.0, LocalDateTime.now().minusMinutes(2), null);
        LocationPoint legacyStale = new LocationPoint(37.49, 127.0, LocalDateTime.now().minusMinutes(20), null);
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.range("location:trail:2", 0, 899))
                .willReturn(java.util.List.of(recent, stale, legacyRecent, legacyStale));

        // when
        LocationTrailResponse response = queryService.getLocationTrail(2L, 1L);

        // then
        assertThat(response.trail()).containsExactly(legacyRecent, recent);
    }

    @Test
    @DisplayName("보호자가 가족 시니어 목록을 조회하면 목록에 담긴 시니어마다 REST_FAMILY 기록을 남긴다")
    void 가족_목록_조회는_시니어마다_REST_FAMILY_기록을_남긴다() {
        // given
        Member seniorMember = member(2L);
        SeniorProfile seniorProfile = seniorProfile(10L, seniorMember);
        FamilyMembership membership =
                FamilyMembership.createMembership(seniorProfile.getFamily(), member(1L));
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.of(membership));
        given(seniorProfileRepository.findAllByFamilyIdWithMember(org.mockito.ArgumentMatchers.any()))
                .willReturn(java.util.List.of(seniorProfile));
        given(seniorLocationRepository.findAllById(java.util.List.of(2L)))
                .willReturn(java.util.List.of(SeniorLocation.of(2L, 37.5, 127.0)));

        // when
        queryService.getTrackedSeniors(1L);

        // then
        then(locationAccessLogService).should().record(1L, 2L, LocationAccessPath.REST_FAMILY);
    }

    @Test
    @DisplayName("실시간 트랜잭션 안에서 체류 상태를 읽다 DB 오류가 나면 예외를 던지고 최신 위치, 이동 경로, 방송, 위치 이벤트를 남기지 않는다")
    void 체류_상태_DB_오류면_예외를_던지고_부수효과를_남기지_않는다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member(1L))));
        willThrow(new QueryTimeoutException("DB timeout")).given(stayStateService).find(1L);

        // when & then
        assertThatThrownBy(() -> ingestService().applyRealtime(request, 1L))
                .isInstanceOf(QueryTimeoutException.class);
        then(transactionManager).should(never()).commit(any());
        then(safeZoneAlertService).should(never()).handleSafeZoneTransition(any(), any(), any(), any());
        then(seniorLocationRepository).should(never()).save(any());
        then(redisTemplate).should(never()).opsForList();
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
        then(eventPublisher).should(never()).publishEvent(any(SeniorLocationUpdatedEvent.class));
    }

    @Test
    @DisplayName("같은 자리 위치는 체류 상태를 잠그지도 쓰지도 않고 읽기만 하며, 커밋한 뒤에 최신 위치, 이동 경로, 방송, 위치 이벤트를 처리한다")
    void 같은_자리_위치는_상태를_잠그지_않고_커밋한_뒤에_최신_위치와_방송을_처리한다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member(1L))));
        stayStateService.states.put(1L, SeniorStayState.start(1L, 37.5001, 127.0, LocalDateTime.now(), null, null));
        given(redisTemplate.opsForList()).willReturn(listOperations);

        // when
        ingestService().applyRealtime(request, 1L);

        // then
        InOrder order = inOrder(stayStateService, transactionManager, seniorLocationRepository, listOperations,
                messagingTemplate, eventPublisher);
        order.verify(stayStateService).find(1L);
        order.verify(transactionManager).commit(any());
        order.verify(seniorLocationRepository).save(any(SeniorLocation.class));
        order.verify(listOperations).leftPush(eq("location:trail:1"), any(LocationPoint.class));
        order.verify(messagingTemplate).convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
        order.verify(eventPublisher).publishEvent(any(SeniorLocationUpdatedEvent.class));
        then(stayStateService).should(never()).lockForChange(any());
        then(stayStateService).should(never()).create(any());
        then(parentLocationRepository).should(never()).findAllByMember(any());
        then(safeZoneAlertService).should(never()).handleSafeZoneTransition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("커밋한 뒤 최신 위치 저장이 실패하면 예외를 던지고 이동 경로·방송·위치 이벤트는 처리하지 않는다")
    void 커밋_후_최신_위치_저장이_실패하면_예외를_던진다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member(1L))));
        given(parentLocationRepository.findAllByMember(any())).willReturn(java.util.List.of());
        willThrow(new QueryTimeoutException("Redis command timed out"))
                .given(seniorLocationRepository).save(any(SeniorLocation.class));

        // when & then
        assertThatThrownBy(() -> ingestService().applyRealtime(request, 1L))
                .isInstanceOf(QueryTimeoutException.class);
        then(transactionManager).should().commit(any());
        then(redisTemplate).should(never()).opsForList();
        then(messagingTemplate).should(never()).convertAndSend(any(String.class), any(Object.class));
        then(eventPublisher).should(never()).publishEvent(any(SeniorLocationUpdatedEvent.class));
    }

    @Test
    @DisplayName("커밋한 뒤 이동 경로 저장이 실패해도 방송과 위치 이벤트를 처리하고 위치 응답을 돌려준다")
    void 커밋_후_이동_경로_저장이_실패해도_방송과_이벤트를_처리한다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member(1L))));
        given(parentLocationRepository.findAllByMember(any())).willReturn(java.util.List.of());
        given(redisTemplate.opsForList()).willReturn(listOperations);
        given(listOperations.leftPush(eq("location:trail:1"), any(LocationPoint.class)))
                .willThrow(new QueryTimeoutException("Redis command timed out"));

        // when
        LocationUpdateResponse response = ingestService().applyRealtime(request, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        then(seniorLocationRepository).should().save(any(SeniorLocation.class));
        then(messagingTemplate).should().convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
        then(eventPublisher).should().publishEvent(any(SeniorLocationUpdatedEvent.class));
    }

    @Test
    @DisplayName("커밋한 뒤 방송이 실패해도 위치 이벤트를 처리하고 위치 응답을 돌려준다")
    void 커밋_후_방송이_실패해도_위치_이벤트를_처리한다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member(1L))));
        given(parentLocationRepository.findAllByMember(any())).willReturn(java.util.List.of());
        given(redisTemplate.opsForList()).willReturn(listOperations);
        willThrow(new IllegalStateException("outbound channel closed"))
                .given(messagingTemplate).convertAndSend(eq("/topic/location/senior/1"), any(Object.class));

        // when
        LocationUpdateResponse response = ingestService().applyRealtime(request, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        then(eventPublisher).should().publishEvent(any(SeniorLocationUpdatedEvent.class));
    }

    @Test
    @DisplayName("커밋한 뒤 위치 이벤트 발행이 실패해도 위치 응답을 돌려준다")
    void 커밋_후_위치_이벤트_발행이_실패해도_위치_응답을_돌려준다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(1L, 37.5, 127.0, null);
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(seniorProfile(10L, member(1L))));
        given(parentLocationRepository.findAllByMember(any())).willReturn(java.util.List.of());
        given(redisTemplate.opsForList()).willReturn(listOperations);
        willThrow(new IllegalStateException("event listener failed"))
                .given(eventPublisher).publishEvent(any(SeniorLocationUpdatedEvent.class));

        // when
        LocationUpdateResponse response = ingestService().applyRealtime(request, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        then(messagingTemplate).should().convertAndSend(eq("/topic/location/senior/1"), any(LocationUpdateResponse.class));
    }

    /** 운영 RedisConfig의 값 직렬화기로 저장·읽기를 한 번 거친 값. 실제 Redis가 돌려주는 모양과 같다. */
    @SuppressWarnings("unchecked")
    private Object throughRedisSerializer(Object value) {
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) new RedisConfig(null)
                .redisTemplate(mock(RedisConnectionFactory.class)).getValueSerializer();
        Object restored = serializer.deserialize(serializer.serialize(value));
        // 타입 정보가 저장되지 않아 원래 타입이 아니라 Map으로 돌아온다(LLD-0097의 원인).
        assertThat(restored).isInstanceOf(LinkedHashMap.class);
        return restored;
    }

    private Member member(Long id) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private SeniorProfile seniorProfile(Long id, Member member) {
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(seniorProfile, "id", id);
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        return seniorProfile;
    }
}
