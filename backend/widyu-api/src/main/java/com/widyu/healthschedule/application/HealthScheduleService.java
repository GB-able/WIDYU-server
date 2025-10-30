package com.widyu.healthschedule.application;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.dto.request.HealthScheduleCreateRequest;
import com.widyu.healthschedule.dto.request.HealthScheduleUpdateRequest;
import com.widyu.healthschedule.dto.response.HealthScheduleResponse;
import com.widyu.healthschedule.repository.HealthScheduleRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthScheduleService {

    private final HealthScheduleRepository healthScheduleRepository;

    @Transactional
    public HealthScheduleResponse createHealthSchedule(HealthScheduleCreateRequest request) {
        HealthSchedule healthSchedule = HealthSchedule.create(
                request.scheduleName(),
                request.placeAddress(),
                request.latitude(),
                request.longitude(),
                request.scheduledAt()
        );

        HealthSchedule saved = healthScheduleRepository.save(healthSchedule);

        return HealthScheduleResponse.from(saved);
    }

    @Transactional
    public HealthScheduleResponse updateHealthSchedule(Long healthScheduleId, HealthScheduleUpdateRequest request) {
        HealthSchedule healthSchedule = healthScheduleRepository.findById(healthScheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "건강 일정을 찾을 수 없습니다."));

        healthSchedule.update(
                request.scheduleName(),
                request.placeAddress(),
                request.latitude(),
                request.longitude(),
                request.scheduledAt(),
                request.progressStatus()
        );

        return HealthScheduleResponse.from(healthSchedule);
    }

    @Transactional
    public void deleteHealthSchedule(Long healthScheduleId) {
        HealthSchedule healthSchedule = healthScheduleRepository.findById(healthScheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "건강 일정을 찾을 수 없습니다."));

        healthScheduleRepository.delete(healthSchedule);
    }
}