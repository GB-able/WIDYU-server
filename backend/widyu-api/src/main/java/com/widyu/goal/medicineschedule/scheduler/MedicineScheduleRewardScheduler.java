package com.widyu.goal.medicineschedule.scheduler;

import com.widyu.global.entity.Status;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.Member;
import com.widyu.member.application.SeniorProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MedicineScheduleRewardScheduler {

    private static final long POINTS_PER_MEDICATION = 10L;
    private static final long BONUS_POINTS_FOR_COMPLETION = 20L;

    private final MedicationProofRepository medicationProofRepository;
    private final MedicineScheduleRepository medicineScheduleRepository;
    private final SeniorProfileService seniorProfileService;

    /**
     * 매일 자정에 실행: 전날(00:00~23:59) 복용 기록 확인 후 포인트 정산
     * - 의약품 1회 복용 시: 10p
     * - 하루 의약품 복용 일정 모두 달성 시: 추가 20p
     *   (예: 2회 모두 복용: 40p, 3회 모두 복용: 50p)
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void settleDailyMedicationPoints() {
        log.info("의약품 복용 포인트 정산 시작");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = yesterday.atTime(LocalTime.MAX);

        // 전날 복용 기록이 있는 모든 회원 조회
        List<Member> membersWithProofs = medicationProofRepository
                .findDistinctMembersByVerifiedAtBetween(startOfDay, endOfDay);

        log.info("포인트 정산 대상 회원 수: {}", membersWithProofs.size());

        int successCount = 0;
        int failureCount = 0;

        for (Member member : membersWithProofs) {
            try {
                settleMemberDailyPoints(member, startOfDay, endOfDay);
                successCount++;
            } catch (Exception e) {
                log.error("회원 포인트 정산 실패: memberId={}, error={}",
                        member.getId(), e.getMessage(), e);
                failureCount++;
            }
        }

        log.info("의약품 복용 포인트 정산 완료: 성공={}, 실패={}", successCount, failureCount);
    }

    private void settleMemberDailyPoints(Member member,
                                         LocalDateTime startOfDay,
                                         LocalDateTime endOfDay) {
        // 전날 복용 인증 횟수
        long proofCount = medicationProofRepository
                .countByMemberAndVerifiedAtBetween(member, startOfDay, endOfDay);

        // 전날 기준 활성화된 총 복용 일정 수
        long totalSchedules = medicineScheduleRepository
                .countByMemberAndStatus(member, Status.ACTIVE);

        if (proofCount == 0) {
            log.debug("복용 인증 기록 없음: memberId={}", member.getId());
            return;
        }

        // 포인트 계산: 1회당 10p
        long points = proofCount * POINTS_PER_MEDICATION;

        // 모든 일정 완료 시 보너스 20p 추가
        if (proofCount == totalSchedules && totalSchedules > 0) {
            points += BONUS_POINTS_FOR_COMPLETION;
            log.info("모든 복용 일정 완료 보너스 적용: memberId={}, proofCount={}/{}",
                    member.getId(), proofCount, totalSchedules);
        }

        // 포인트 적립
        seniorProfileService.addPointsToMember(member.getId(), points);

        log.info("의약품 복용 포인트 정산 완료: memberId={}, date={}, proofCount={}/{}, totalPoints={}",
                member.getId(), startOfDay.toLocalDate(), proofCount, totalSchedules, points);
    }
}