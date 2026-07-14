package com.widyu.goal.healthschedule.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.goal.healthschedule.dto.request.HealthSchedulePointGetRequest;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthScheduleRewardService 예외 처리 단위 테스트")
class HealthScheduleRewardServiceTest {

    @Mock private HealthScheduleRepository healthScheduleRepository;

    @InjectMocks
    private HealthScheduleRewardService healthScheduleRewardService;

    @Test
    @DisplayName("포인트 적립 대상 건강 일정이 없으면 BAD_REQUEST 예외를 던진다")
    void 포인트_적립_대상_건강_일정이_없으면_예외가_발생한다() {
        // given
        given(healthScheduleRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> healthScheduleRewardService.accumulateHealthSchedulePoints(
                new HealthSchedulePointGetRequest(1L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("건강 일정을 찾을 수 없습니다.");
    }
}
