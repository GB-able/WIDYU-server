package com.widyu.goal.medicineschedule.application;

import com.widyu.global.entity.Status;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Member;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 복약 현황 조회. 목표 홈과 홈 카드가 같은 규칙으로 집계하도록 한 곳에 둔다 (#1059).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayMedicationQuery {

    private final MedicineScheduleRepository medicineScheduleRepository;
    private final MedicationProofRepository medicationProofRepository;

    /** 오늘 유효한 일정이 없으면 인증을 조회하지 않고 빈 현황을 반환한다. */
    public TodayMedication find(Member member, LocalDate today) {
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findEffectiveByMemberAndDateWithDetails(member, Status.ACTIVE, today);

        if (schedules.isEmpty()) {
            return new TodayMedication(schedules, Map.of());
        }

        // 스케줄 수정·삭제로 오늘 유효 목록에서 빠진 버전의 인증은 제외한다 (takenCount가 totalCount를 넘지 않도록)
        Set<Long> scheduleIds = schedules.stream()
                .map(MedicineSchedule::getId)
                .collect(Collectors.toSet());

        Map<Long, MedicationProof> proofsByScheduleId = medicationProofRepository
                .findByMemberIdAndDateRange(member.getId(), today, today)
                .stream()
                .filter(proof -> scheduleIds.contains(proof.getMedicineSchedule().getId()))
                .collect(Collectors.toMap(
                        proof -> proof.getMedicineSchedule().getId(),
                        proof -> proof,
                        // 조회 쿼리에 정렬이 없어 중복 인증 시 대표를 verifiedAt 최초순으로 고정한다.
                        BinaryOperator.minBy(Comparator.comparing(MedicationProof::getVerifiedAt))
                ));

        return new TodayMedication(schedules, proofsByScheduleId);
    }
}
