package com.widyu.goal.healthschedule.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.goal.healthschedule.dto.request.HealthSchedulePointGetRequest;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthScheduleRewardService {

    private final HealthScheduleRepository healthScheduleRepository;

    @Transactional
    public void accumulateHealthSchedulePoints(HealthSchedulePointGetRequest healthSchedulePointGetRequest) {
        HealthSchedule healthSchedule = healthScheduleRepository.findById(
                        healthSchedulePointGetRequest.healthScheduleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "건강 일정을 찾을 수 없습니다."));

        // 포인트 정립 로직 필요
        healthSchedule.claimReward();
    }
}
