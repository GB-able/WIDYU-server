package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.SeniorLocation;
import com.widyu.location.realtime.dto.LocationPoint;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.realtime.event.SeniorLocationUpdatedEvent;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RealtimeLocationService 예외 처리 단위 테스트")
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

    @InjectMocks
    private RealtimeLocationService realtimeLocationService;

    @Test
    @DisplayName("인증 회원과 요청 회원이 다르면 FORBIDDEN 예외를 던지고 위치를 저장하지 않는다")
    void 인증_회원과_요청_회원이_다르면_예외가_발생한다() {
        // given
        LocationUpdateRequest request = LocationUpdateRequest.of(2L, 37.5, 127.0, null);

        // when & then
        assertThatThrownBy(() -> realtimeLocationService.updateAndBroadcast(request, 1L))
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
        assertThatThrownBy(() -> realtimeLocationService.updateAndBroadcast(request, 1L))
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
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("location:stay:1")).willReturn(null);
        given(parentLocationRepository.findAllByMember(member)).willReturn(java.util.List.of());

        // when
        LocationUpdateResponse response = realtimeLocationService.updateAndBroadcast(request, 1L);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.latitude()).isEqualTo(37.5);
        assertThat(response.longitude()).isEqualTo(127.0);
        then(seniorLocationRepository).should().save(any(SeniorLocation.class));
        then(eventPublisher).should().publishEvent(any(SeniorLocationUpdatedEvent.class));
        then(listOperations).should().leftPush(eq("location:trail:1"), any(LocationPoint.class));
        then(valueOperations).should().set(eq("location:stay:1"), any(StayInfo.class), eq(86400L), eq(TimeUnit.SECONDS));
        then(safeZoneAlertService).should().handleSafeZoneTransition(1L, null, null);
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
        assertThatThrownBy(() -> realtimeLocationService.getLastLocation(2L, 1L))
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
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("location:stay:2")).willReturn(null);
        given(seniorLocationRepository.findBySeniorId(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> realtimeLocationService.getLastLocation(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND)
                .hasMessageContaining("최근 위치 정보가 없습니다.");
    }

    @Test
    @DisplayName("집 밖의 체류 위치이면 외출 중으로 반환한다")
    void 집_밖의_체류위치이면_외출중으로_반환한다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("location:stay:1"))
                .willReturn(StayInfo.of(37.5, 127.0, null, null));

        // when
        Boolean isOuting = realtimeLocationService.getOutingStatus(1L);

        // then
        assertThat(isOuting).isTrue();
    }

    @Test
    @DisplayName("연결되지 않은 보호자가 이동 경로 조회 시 FORBIDDEN 예외를 던진다")
    void 연결되지_않은_보호자_이동_경로_조회_시_예외가_발생한다() {
        // given
        SeniorProfile seniorProfile = seniorProfile(10L, member(2L));
        given(seniorProfileRepository.findByMemberId(2L)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> realtimeLocationService.getLocationTrail(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("해당 시니어의 위치를 조회할 권한이 없습니다.");
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
