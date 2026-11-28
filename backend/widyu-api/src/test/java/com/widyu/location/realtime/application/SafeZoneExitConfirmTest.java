package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.widyu.fcm.event.safezone.dto.SafeZoneEnterEvent;
import com.widyu.fcm.event.safezone.dto.SafeZoneExitEvent;
import com.widyu.global.entity.Status;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.stay.SafeZoneAlertState;
import com.widyu.location.stay.SeniorStayState;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** 안심구역 이탈 확정 지연(LLD-0192): 짧은 GPS 튐은 알리지 않고, 확정 시간 넘게 밖이면 이탈을 한 번 알린다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("안심구역 이탈 확정 지연")
class SafeZoneExitConfirmTest {

    private static final long MEMBER_ID = 1L;
    private static final double HOME_LAT = 37.5665;
    private static final double HOME_LNG = 126.9780;
    private static final double AWAY_LAT = 37.5765; // 약 1.1km, 안심구역(75m) 밖
    private static final double EDGE_IN_LAT = 37.5671; // 집에서 약 67m, 안심구역 안
    private static final double EDGE_OUT_FAR_LAT = 37.5676; // 약 122m, 밖. EDGE_IN에서 약 55m
    private static final double EDGE_OUT_NEAR_LAT = 37.5673; // 약 89m, 밖. EDGE_IN에서 약 22m(같은 체류 반경)

    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private ParentLocationRepository parentLocationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final FakeSeniorStayStateService stayStateService = new FakeSeniorStayStateService();
    private LocationJudgeService judgeService;

    @BeforeEach
    void setUp() {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        SeniorProfile profile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(member, "seniorProfile", profile);
        given(seniorProfileRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of(profile));
        given(parentLocationRepository.findAllByMember(member)).willReturn(List.of(ParentLocation.builder()
                .member(member).locationType(LocationType.HOME).placeAddress("서울")
                .latitude(HOME_LAT).longitude(HOME_LNG).name("집").status(Status.ACTIVE).build()));
        judgeService = new LocationJudgeService(seniorProfileRepository, parentLocationRepository, stayStateService,
                new SafeZoneAlertService(eventPublisher));
        ReflectionTestUtils.setField(judgeService, "exitConfirmSec", 60L);
    }

    @Test
    @DisplayName("밖으로 한 번 찍히고 확정 시간 안에 돌아오면 알림이 없고 안심구역 상태와 체류 시작 시각이 그대로다")
    void 확정_시간_안에_돌아오면_알림이_없고_구역_상태가_유지된다() {
        // given
        LocationUpdateResponse atHome = send(HOME_LAT);

        // when
        LocationUpdateResponse spike = send(AWAY_LAT);
        LocationUpdateResponse back = send(HOME_LAT);

        // then
        assertThat(spike.locationType()).isEqualTo("HOME");
        assertThat(spike.stayStartTime()).isEqualTo(atHome.stayStartTime());
        assertThat(back.stayStartTime()).isEqualTo(atHome.stayStartTime());
        SeniorStayState state = state();
        assertThat(state.getLatitude()).isEqualTo(HOME_LAT);
        assertThat(state.getPendingExitSince()).isNull();
        assertThat(state.getAlertState()).isNull();
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("확정 시간 넘게 계속 밖이면 이탈로 확정하고 Z01을 한 번 발행한다")
    void 확정_시간_넘게_밖이면_이탈을_한_번_알린다() {
        // given
        send(HOME_LAT);
        send(AWAY_LAT);
        ageExitHold();

        // when
        LocationUpdateResponse confirmed = send(AWAY_LAT);
        send(AWAY_LAT + 0.001);

        // then
        assertThat(confirmed.locationType()).isNull();
        assertThat(state().getPendingExitSince()).isNull();
        assertThat(state().getAlertState()).isEqualTo(SafeZoneAlertState.EXITED);
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneExitEvent.class));
    }

    @Test
    @DisplayName("확정 대기 중에는 구역 상태가 바뀐 시각(체류 시간 기준)이 그대로이고, 이탈을 확정하면 확정 시각으로 바뀐다")
    void 확정_대기_중_구역_시각은_그대로이고_확정하면_바뀐다() {
        // given
        LocationUpdateResponse atHome = send(HOME_LAT);
        LocalDateTime zoneChangedAtHome = state().getZoneChangedAt();

        // when
        LocationUpdateResponse spike = send(AWAY_LAT);

        // then: 응답의 체류 시작도 구역 상태 시각이다(LLD-0191)
        assertThat(state().getZoneChangedAt()).isEqualTo(zoneChangedAtHome);
        assertThat(spike.stayStartTime()).isEqualTo(atHome.stayStartTime());

        // when
        ageExitHold();
        LocalDateTime beforeConfirm = LocalDateTime.now().minusNanos(1_000);
        LocationUpdateResponse confirmed = send(AWAY_LAT);

        // then
        assertThat(state().getZoneChangedAt()).isAfter(beforeConfirm);
        assertThat(state().getZoneChangedAt()).isEqualTo(state().getStayStartedAt());
        assertThat(confirmed.stayStartTime()).isEqualTo(state().getZoneChangedAt());
    }

    @Test
    @DisplayName("확정 대기 중 체류 지점 30m 안의 밖 위치가 와도 같은 자리 지름길을 타지 않고 이탈을 확정한다")
    void 확정_대기_중_같은_자리_위치도_이탈을_확정한다() {
        // given
        send(EDGE_IN_LAT);
        send(EDGE_OUT_FAR_LAT);
        ageExitHold();

        // when
        LocationUpdateResponse confirmed = send(EDGE_OUT_NEAR_LAT);

        // then
        assertThat(confirmed.locationType()).isNull();
        assertThat(state().getLatitude()).isEqualTo(EDGE_OUT_NEAR_LAT);
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneExitEvent.class));
    }

    @Test
    @DisplayName("확정 대기 중 체류 지점 30m 안의 안 위치로 돌아오면 대기만 지우고 체류 지점을 옮기지 않는다")
    void 확정_대기_중_같은_자리로_돌아오면_대기만_지운다() {
        // given
        LocationUpdateResponse atHome = send(HOME_LAT);
        send(AWAY_LAT);

        // when
        LocationUpdateResponse back = send(HOME_LAT + 0.0001);

        // then
        assertThat(back.stayStartTime()).isEqualTo(atHome.stayStartTime());
        assertThat(state().getLatitude()).isEqualTo(HOME_LAT);
        assertThat(state().getPendingExitSince()).isNull();
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("확정 시간이 0이면 밖으로 찍힌 첫 위치에서 바로 이탈을 알린다")
    void 확정_시간이_0이면_즉시_이탈한다() {
        // given
        ReflectionTestUtils.setField(judgeService, "exitConfirmSec", 0L);
        send(HOME_LAT);

        // when
        LocationUpdateResponse away = send(AWAY_LAT);

        // then
        assertThat(away.locationType()).isNull();
        assertThat(state().getPendingExitSince()).isNull();
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneExitEvent.class));
    }

    @Test
    @DisplayName("귀가 알림 30분 묶음이 끝난 뒤 짧게 튀면 알림이 없고, 묶음 안에서 확정된 재이탈과 재진입도 알리지 않는다")
    void 돌아옴_묶음_규칙과_함께_동작한다() {
        // given: 실제 외출과 귀가(Z01, Z02 한 번씩)
        send(HOME_LAT);
        send(AWAY_LAT);
        ageExitHold();
        send(AWAY_LAT);
        send(HOME_LAT);

        // when: 묶음 안에서 확정 시간 넘게 밖에 있다 돌아온다(LLD-0190 묶음)
        send(AWAY_LAT);
        ageExitHold();
        send(AWAY_LAT);
        send(HOME_LAT);
        // when: 묶음이 끝난 뒤 13초짜리 튐(#1179 개발 서버 사례)
        ReflectionTestUtils.setField(state(), "alertUntil", LocalDateTime.now().minusMinutes(1));
        send(AWAY_LAT);
        send(HOME_LAT);

        // then
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneExitEvent.class));
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneEnterEvent.class));
        assertThat(state().getLocationType()).isEqualTo("HOME");
        assertThat(state().getAlertState()).isEqualTo(SafeZoneAlertState.RETURNED);
    }

    private LocationUpdateResponse send(double latitude) {
        return judgeService.judge(LocationUpdateRequest.of(MEMBER_ID, latitude, HOME_LNG, null),
                System.currentTimeMillis());
    }

    /** 밖으로 처음 찍힌 뒤 확정 시간(60초)이 지난 것으로 만든다. */
    private void ageExitHold() {
        ReflectionTestUtils.setField(state(), "pendingExitSince", LocalDateTime.now().minusSeconds(61));
    }

    private SeniorStayState state() {
        return stayStateService.states.get(MEMBER_ID);
    }
}
