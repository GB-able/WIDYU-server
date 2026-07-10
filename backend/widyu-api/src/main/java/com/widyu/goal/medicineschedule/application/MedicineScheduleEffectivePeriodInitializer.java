package com.widyu.goal.medicineschedule.application;

import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MedicineScheduleEffectivePeriodInitializer implements ApplicationRunner {

    private final MedicineScheduleRepository medicineScheduleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updatedCount = medicineScheduleRepository.backfillMissingEffectiveFrom();
        if (updatedCount > 0) {
            log.info("약 복용 스케줄 effectiveFrom 보정 완료: count={}", updatedCount);
        }
    }
}
