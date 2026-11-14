package com.widyu.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.home.application.GuardianHomeService.GuardianHomeCards;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuardianHomeFacade 홈 카드 위치 열람 기록 단위 테스트")
class GuardianHomeFacadeTest {

    private static final Long GUARDIAN_ID = 1L;
    private static final Long SENIOR_ID = 2L;

    @Mock private GuardianHomeService guardianHomeService;
    @Mock private LocationAccessLogService locationAccessLogService;

    @InjectMocks
    private GuardianHomeFacade guardianHomeFacade;

    @Test
    @DisplayName("보호자가 홈 카드를 열면 카드를 읽은 뒤 외출 상태를 읽은 시니어에 대해 HOME_OUTING 열람 기록을 남긴다")
    void 홈_카드를_읽은_뒤_HOME_OUTING_기록을_남긴다() {
        // given
        GuardianHomeCardsResponse response = mock(GuardianHomeCardsResponse.class);
        given(guardianHomeService.getHomeCards(SENIOR_ID))
                .willReturn(new GuardianHomeCards(GUARDIAN_ID, SENIOR_ID, response));

        // when
        GuardianHomeCardsResponse result = guardianHomeFacade.getHomeCards(SENIOR_ID);

        // then
        assertThat(result).isSameAs(response);
        InOrder inOrder = inOrder(guardianHomeService, locationAccessLogService);
        inOrder.verify(guardianHomeService).getHomeCards(SENIOR_ID);
        inOrder.verify(locationAccessLogService).record(GUARDIAN_ID, SENIOR_ID, LocationAccessPath.HOME_OUTING);
    }

    @Test
    @DisplayName("열람 기록이 예외를 던져도 홈 카드 응답은 그대로 돌려준다")
    void 열람_기록이_실패해도_홈_카드를_돌려준다() {
        // given
        GuardianHomeCardsResponse response = mock(GuardianHomeCardsResponse.class);
        given(guardianHomeService.getHomeCards(SENIOR_ID))
                .willReturn(new GuardianHomeCards(GUARDIAN_ID, SENIOR_ID, response));
        willThrow(new IllegalStateException("DB 장애"))
                .given(locationAccessLogService)
                .record(GUARDIAN_ID, SENIOR_ID, LocationAccessPath.HOME_OUTING);

        // when
        GuardianHomeCardsResponse result = guardianHomeFacade.getHomeCards(SENIOR_ID);

        // then
        assertThat(result).isSameAs(response);
    }

    @Test
    @DisplayName("접근 권한이 없어 홈 카드 조회가 막히면 열람 기록을 남기지 않는다")
    void 권한이_없으면_열람_기록을_남기지_않는다() {
        // given
        given(guardianHomeService.getHomeCards(SENIOR_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        // when & then
        assertThatThrownBy(() -> guardianHomeFacade.getHomeCards(SENIOR_ID))
                .isInstanceOf(BusinessException.class);
        then(locationAccessLogService).shouldHaveNoInteractions();
    }
}
