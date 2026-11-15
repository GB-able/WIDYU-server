package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationQueryFacade 위치 조회 열람 기록 단위 테스트")
class LocationQueryFacadeTest {

    private static final Long GUARDIAN_ID = 1L;
    private static final Long SENIOR_ID = 2L;
    private static final Long OTHER_SENIOR_ID = 3L;

    @Mock private LocationQueryService locationQueryService;
    @Mock private LocationAccessLogService locationAccessLogService;

    @InjectMocks
    private LocationQueryFacade locationQueryFacade;

    @Test
    @DisplayName("보호자가 마지막 위치를 조회하면 위치를 읽은 뒤 REST_LAST 열람 기록을 남긴다")
    void 마지막_위치를_읽은_뒤_REST_LAST_기록을_남긴다() {
        // given
        LocationUpdateResponse response = mock(LocationUpdateResponse.class);
        given(locationQueryService.getLastLocation(SENIOR_ID, GUARDIAN_ID)).willReturn(response);

        // when
        LocationUpdateResponse result = locationQueryFacade.getLastLocation(SENIOR_ID, GUARDIAN_ID);

        // then
        assertThat(result).isSameAs(response);
        InOrder inOrder = inOrder(locationQueryService, locationAccessLogService);
        inOrder.verify(locationQueryService).getLastLocation(SENIOR_ID, GUARDIAN_ID);
        inOrder.verify(locationAccessLogService).record(GUARDIAN_ID, SENIOR_ID, LocationAccessPath.REST_LAST);
    }

    @Test
    @DisplayName("보호자가 이동 경로를 조회하면 경로를 읽은 뒤 REST_TRAIL 열람 기록을 남긴다")
    void 이동_경로를_읽은_뒤_REST_TRAIL_기록을_남긴다() {
        // given
        LocationTrailResponse response = mock(LocationTrailResponse.class);
        given(locationQueryService.getLocationTrail(SENIOR_ID, GUARDIAN_ID)).willReturn(response);

        // when
        LocationTrailResponse result = locationQueryFacade.getLocationTrail(SENIOR_ID, GUARDIAN_ID);

        // then
        assertThat(result).isSameAs(response);
        InOrder inOrder = inOrder(locationQueryService, locationAccessLogService);
        inOrder.verify(locationQueryService).getLocationTrail(SENIOR_ID, GUARDIAN_ID);
        inOrder.verify(locationAccessLogService).record(GUARDIAN_ID, SENIOR_ID, LocationAccessPath.REST_TRAIL);
    }

    @Test
    @DisplayName("보호자가 가족 시니어 목록을 조회하면 목록을 읽은 뒤 목록에 담긴 시니어마다 REST_FAMILY 기록을 남긴다")
    void 가족_목록을_읽은_뒤_시니어마다_REST_FAMILY_기록을_남긴다() {
        // given
        List<TrackedSeniorResponse> seniors = List.of(
                TrackedSeniorResponse.builder().memberId(SENIOR_ID).build(),
                TrackedSeniorResponse.builder().memberId(OTHER_SENIOR_ID).build());
        given(locationQueryService.getTrackedSeniors(GUARDIAN_ID)).willReturn(seniors);

        // when
        List<TrackedSeniorResponse> result = locationQueryFacade.getTrackedSeniors(GUARDIAN_ID);

        // then
        assertThat(result).isSameAs(seniors);
        InOrder inOrder = inOrder(locationQueryService, locationAccessLogService);
        inOrder.verify(locationQueryService).getTrackedSeniors(GUARDIAN_ID);
        inOrder.verify(locationAccessLogService).record(GUARDIAN_ID, SENIOR_ID, LocationAccessPath.REST_FAMILY);
        inOrder.verify(locationAccessLogService)
                .record(GUARDIAN_ID, OTHER_SENIOR_ID, LocationAccessPath.REST_FAMILY);
    }

    @Test
    @DisplayName("열람 기록이 예외를 던져도 위치 응답은 그대로 돌려준다")
    void 열람_기록이_실패해도_위치_응답을_돌려준다() {
        // given
        LocationUpdateResponse response = mock(LocationUpdateResponse.class);
        given(locationQueryService.getLastLocation(SENIOR_ID, GUARDIAN_ID)).willReturn(response);
        willThrow(new IllegalStateException("DB 장애"))
                .given(locationAccessLogService).record(GUARDIAN_ID, SENIOR_ID, LocationAccessPath.REST_LAST);

        // when
        LocationUpdateResponse result = locationQueryFacade.getLastLocation(SENIOR_ID, GUARDIAN_ID);

        // then
        assertThat(result).isSameAs(response);
    }

    @Test
    @DisplayName("권한이 없어 위치 조회가 막히면 열람 기록을 남기지 않는다")
    void 권한이_없으면_열람_기록을_남기지_않는다() {
        // given
        given(locationQueryService.getLastLocation(SENIOR_ID, GUARDIAN_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        // when & then
        assertThatThrownBy(() -> locationQueryFacade.getLastLocation(SENIOR_ID, GUARDIAN_ID))
                .isInstanceOf(BusinessException.class);
        then(locationAccessLogService).shouldHaveNoInteractions();
    }
}
