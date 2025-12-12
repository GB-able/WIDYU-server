package com.widyu.goal.home.application;

import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.home.dto.response.FamilyListResponse;
import com.widyu.goal.home.dto.response.FamilyMemberResponse;
import com.widyu.goal.home.dto.response.GuardianGoalHomeResponse;
import com.widyu.goal.home.dto.response.GuardianGoalStatsResponse;
import com.widyu.goal.home.dto.response.SeniorGoalHomeResponse;
import com.widyu.goal.home.dto.response.SeniorWeeklyGoalStatusResponse;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.goal.DailyGoalStatus;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineCategory;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.medicine.MedicineScheduleDetail;
import com.widyu.walk.Walk;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalHomeService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final FamilyConnectionRepository familyConnectionRepository;
    private final MedicineScheduleRepository medicineScheduleRepository;
    private final MedicationProofRepository medicationProofRepository;
    private final WalkRepository walkRepository;
    private final HealthScheduleRepository healthScheduleRepository;
    private final MemberRepository memberRepository;
    private final MemberUtil memberUtil;

    public FamilyListResponse getFamilyList() {
        Member currentMember = memberUtil.getCurrentMember();

        List<FamilyConnection> familyConnections =
                familyConnectionRepository.findAllByGuardianIdWithSeniorAndMember(currentMember.getId());

        List<FamilyMemberResponse> families = familyConnections.stream()
                .map(FamilyMemberResponse::from)
                .toList();

        return FamilyListResponse.of(families);
    }

    /**
     * 시니어 목표 홈 - 목표 조회
     */
    public SeniorGoalHomeResponse getSeniorGoalHome() {
        Member currentMember = memberUtil.getCurrentMember();
        LocalDate today = LocalDate.now();

        // Medicine 정보
        SeniorGoalHomeResponse.MedicineInfo medicineInfo = getSeniorMedicineInfo(currentMember, today);

        // Steps 정보
        SeniorGoalHomeResponse.StepsInfo stepsInfo = getStepsInfo(currentMember, today);

        // Hospital 정보
        SeniorGoalHomeResponse.HospitalInfo hospitalInfo = getUpcomingHospitalInfo(currentMember);

        return new SeniorGoalHomeResponse(medicineInfo, stepsInfo, hospitalInfo);
    }

    /**
     * 시니어 이번 주 목표 달성률 조회
     */
    public SeniorWeeklyGoalStatusResponse getSeniorWeeklyGoalStatus() {
        Member currentMember = memberUtil.getCurrentMember();
        LocalDate today = LocalDate.now();

        // 이번 주 일요일부터 토요일까지
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

        List<DailyGoalStatus> weeklyStatus = new ArrayList<>();
        for (LocalDate date = startOfWeek; !date.isAfter(endOfWeek); date = date.plusDays(1)) {
            DailyGoalStatus status = calculateDailyGoalStatus(currentMember, date);
            weeklyStatus.add(status);
        }

        return new SeniorWeeklyGoalStatusResponse(weeklyStatus);
    }

    /**
     * 보호자 목표 홈 - 현황 조회
     */
    public GuardianGoalStatsResponse getGuardianGoalStats(Long memberId) {
        Member targetMember = getMember(memberId);
        LocalDate today = LocalDate.now();

        // 지난주
        LocalDate lastWeekStart = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);
        Double lastWeekGoalRate = calculateWeeklyGoalRate(targetMember, lastWeekStart, lastWeekEnd);

        // 이번주
        LocalDate thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate thisWeekEnd = thisWeekStart.plusDays(6);
        Double thisWeekGoalRate = calculateWeeklyGoalRate(targetMember, thisWeekStart, thisWeekEnd);

        // 이번주 일별 달성률
        List<Double> thisWeekGoalRates = new ArrayList<>();
        for (LocalDate date = thisWeekStart; !date.isAfter(thisWeekEnd); date = date.plusDays(1)) {
            Double dailyRate = calculateDailyGoalRate(targetMember, date);
            thisWeekGoalRates.add(dailyRate);
        }

        return new GuardianGoalStatsResponse(lastWeekGoalRate, thisWeekGoalRate, thisWeekGoalRates);
    }

    /**
     * 보호자 목표 홈 - 목표 조회
     */
    public GuardianGoalHomeResponse getGuardianGoalHome(Long memberId) {
        Member targetMember = getMember(memberId);
        LocalDate today = LocalDate.now();

        // Medicine 정보
        GuardianGoalHomeResponse.MedicineInfo medicineInfo = getGuardianMedicineInfo(targetMember, today);

        // Steps 정보
        GuardianGoalHomeResponse.StepsInfo stepsInfo = getGuardianStepsInfo(targetMember, today);

        // Hospital 정보
        GuardianGoalHomeResponse.HospitalInfo hospitalInfo = getGuardianHospitalInfo(targetMember);

        return new GuardianGoalHomeResponse(medicineInfo, stepsInfo, hospitalInfo);
    }

    // ===== Private Helper Methods =====

    private SeniorGoalHomeResponse.MedicineInfo getSeniorMedicineInfo(Member member, LocalDate today) {
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(member, Status.ACTIVE);

        if (schedules.isEmpty()) {
            return null;
        }

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // 오늘 복용한 횟수
        int todayTakenCount = (int) medicationProofRepository
                .findByMemberIdAndDateRange(member.getId(), startOfDay, endOfDay)
                .size();

        // 오늘 총 복용 예정 횟수
        int todayTotalCount = schedules.size();

        // 다음 알람 시간 찾기
        LocalTime now = LocalTime.now();
        MedicineSchedule nextSchedule = schedules.stream()
                .filter(s -> s.getAlarmTime().isAfter(now))
                .min(Comparator.comparing(MedicineSchedule::getAlarmTime))
                .orElse(schedules.get(0)); // 오늘 남은 알람이 없으면 첫 번째 스케줄

        // 다음 복용 예정 개수
        int nextDoseCount = nextSchedule.getTotalCount();

        // 알람 시간을 HH:mm 형태로 포맷
        String nextAlarmTime = nextSchedule.getAlarmTime().format(TIME_FORMATTER);

        return new SeniorGoalHomeResponse.MedicineInfo(
                nextSchedule.getId(),
                todayTakenCount,
                todayTotalCount,
                nextDoseCount,
                nextAlarmTime
        );
    }

    private SeniorGoalHomeResponse.StepsInfo getStepsInfo(Member member, LocalDate today) {
        Walk walk = walkRepository.findByMemberAndWalkDate(member, today).orElse(null);

        if (walk != null) {
            return new SeniorGoalHomeResponse.StepsInfo(walk.getActualSteps(), walk.getGoalSteps());
        }

        // 기본 목표가 있으면 반환
        if (member.getSeniorProfile() != null && member.getSeniorProfile().hasDefaultWalkGoal()) {
            return new SeniorGoalHomeResponse.StepsInfo(0, member.getSeniorProfile().getDefaultWalkGoal());
        }

        return null;
    }

    private SeniorGoalHomeResponse.HospitalInfo getUpcomingHospitalInfo(Member member) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime futureLimit = now.plusMonths(1); // 1개월 이내

        List<HealthSchedule> upcomingSchedules = healthScheduleRepository
                .findByMemberIdAndWeek(member.getId(), now, futureLimit);

        if (upcomingSchedules.isEmpty()) {
            return null;
        }

        // 가장 가까운 일정
        HealthSchedule nearest = upcomingSchedules.stream()
                .filter(s -> s.getScheduledAt().isAfter(now))
                .min(Comparator.comparing(HealthSchedule::getScheduledAt))
                .orElse(null);

        if (nearest == null) {
            return null;
        }

        int dday = (int) java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), nearest.getScheduledAt().toLocalDate());

        return new SeniorGoalHomeResponse.HospitalInfo(
                nearest.getId(),
                dday,
                nearest.getScheduledAt(),
                nearest.getScheduleName(),
                nearest.getPlaceAddress()
        );
    }

    private DailyGoalStatus calculateDailyGoalStatus(Member member, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        LocalDate today = LocalDate.now();

        // 날짜가 미래면 NOT_STARTED (기한 전)
        if (date.isAfter(today)) {
            return DailyGoalStatus.NOT_STARTED;
        }

        // 약 스케줄과 걸음 수 확인
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(member, Status.ACTIVE);

        Walk walk = walkRepository.findByMemberAndWalkDate(member, date).orElse(null);

        int totalGoals = schedules.size() + (walk != null ? 1 : 0);
        if (totalGoals == 0) {
            return DailyGoalStatus.NOT_STARTED; // 목표가 없으면 시작 전
        }

        // 완료된 목표 개수
        int completedGoals = 0;

        // 약 복용 체크
        for (MedicineSchedule schedule : schedules) {
            boolean taken = medicationProofRepository.existsByMedicineScheduleAndVerifiedAtBetween(
                    schedule, startOfDay, endOfDay);
            if (taken) {
                completedGoals++;
            }
        }

        // 걸음 수 체크
        if (walk != null && walk.isGoalAchieved()) {
            completedGoals++;
        }

        // 상태 결정
        if (completedGoals == totalGoals) {
            // 모든 목표 완료
            return DailyGoalStatus.COMPLETED;
        } else if (completedGoals > 0) {
            // 일부 완료
            if (date.isBefore(today)) {
                // 기한이 지났는데 일부만 완료 = FAILED
                return DailyGoalStatus.FAILED;
            } else {
                // 오늘이고 일부 완료 = IN_PROGRESS
                return DailyGoalStatus.IN_PROGRESS;
            }
        } else {
            // 아무것도 완료 안 함
            if (date.isBefore(today)) {
                // 기한이 지났는데 미완료 = FAILED
                return DailyGoalStatus.FAILED;
            } else {
                // 오늘인데 아직 시작 안 함 = NOT_STARTED
                return DailyGoalStatus.NOT_STARTED;
            }
        }
    }

    private Double calculateWeeklyGoalRate(Member member, LocalDate startDate, LocalDate endDate) {
        int totalDays = 0;
        int completedDays = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 미래 날짜는 제외
            if (date.isAfter(LocalDate.now())) {
                continue;
            }

            totalDays++;
            Double dailyRate = calculateDailyGoalRate(member, date);
            if (dailyRate >= 1.0) { // 100% 달성
                completedDays++;
            }
        }

        if (totalDays == 0) {
            return 0.0;
        }

        return (double) completedDays / totalDays;
    }

    private Double calculateDailyGoalRate(Member member, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 약 스케줄과 걸음 수 확인
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(member, Status.ACTIVE);

        Walk walk = walkRepository.findByMemberAndWalkDate(member, date).orElse(null);

        int totalGoals = schedules.size() + (walk != null ? 1 : 0);
        if (totalGoals == 0) {
            return 0.0;
        }

        int completedGoals = 0;

        // 약 복용 체크
        for (MedicineSchedule schedule : schedules) {
            boolean taken = medicationProofRepository.existsByMedicineScheduleAndVerifiedAtBetween(
                    schedule, startOfDay, endOfDay);
            if (taken) {
                completedGoals++;
            }
        }

        // 걸음 수 체크
        if (walk != null && walk.isGoalAchieved()) {
            completedGoals++;
        }

        return (double) completedGoals / totalGoals;
    }

    private GuardianGoalHomeResponse.MedicineInfo getGuardianMedicineInfo(Member member, LocalDate today) {
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(member, Status.ACTIVE);

        if (schedules.isEmpty()) {
            return null;
        }

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // 오늘 복용 인증 정보 조회
        Map<Long, MedicationProof> proofMap = medicationProofRepository
                .findByMemberIdAndDateRange(member.getId(), startOfDay, endOfDay)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getMedicineSchedule().getId(),
                        p -> p,
                        (p1, p2) -> p1 // 중복 시 첫 번째 선택
                ));

        int totalCount = 0;
        int takenCount = 0;

        List<GuardianGoalHomeResponse.ScheduleItem> scheduleItems = new ArrayList<>();

        for (MedicineSchedule schedule : schedules) {
            int scheduleTotal = schedule.getTotalCount();
            totalCount += scheduleTotal;

            MedicationProof proof = proofMap.get(schedule.getId());
            boolean taken = proof != null;
            if (taken) {
                takenCount += scheduleTotal;
            }

            List<GuardianGoalHomeResponse.MedicineItem> medicineItems = schedule.getCategories().stream()
                    .flatMap(category -> category.getMedicines().stream())
                    .map(detail -> new GuardianGoalHomeResponse.MedicineItem(
                            detail.getMedicine().getName(),
                            detail.getDose()
                    ))
                    .toList();

            String proofImageUrl = taken && !proof.getProofImageUrls().isEmpty()
                    ? proof.getProofImageUrls().get(0)
                    : null;

            scheduleItems.add(new GuardianGoalHomeResponse.ScheduleItem(
                    schedule.getId(),
                    schedule.getAlarmTime().format(TIME_FORMATTER),
                    taken ? "taken" : "not_taken",
                    proofImageUrl,
                    medicineItems
            ));
        }

        return new GuardianGoalHomeResponse.MedicineInfo(totalCount, takenCount, scheduleItems);
    }

    private GuardianGoalHomeResponse.StepsInfo getGuardianStepsInfo(Member member, LocalDate today) {
        Walk walk = walkRepository.findByMemberAndWalkDate(member, today).orElse(null);

        if (walk != null) {
            return new GuardianGoalHomeResponse.StepsInfo(walk.getActualSteps(), walk.getGoalSteps());
        }

        // 기본 목표가 있으면 반환
        if (member.getSeniorProfile() != null && member.getSeniorProfile().hasDefaultWalkGoal()) {
            return new GuardianGoalHomeResponse.StepsInfo(0, member.getSeniorProfile().getDefaultWalkGoal());
        }

        return null;
    }

    private GuardianGoalHomeResponse.HospitalInfo getGuardianHospitalInfo(Member member) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime futureLimit = now.plusMonths(1);

        List<HealthSchedule> upcomingSchedules = healthScheduleRepository
                .findByMemberIdAndWeek(member.getId(), now, futureLimit);

        if (upcomingSchedules.isEmpty()) {
            return null;
        }

        HealthSchedule nearest = upcomingSchedules.stream()
                .filter(s -> s.getScheduledAt().isAfter(now))
                .min(Comparator.comparing(HealthSchedule::getScheduledAt))
                .orElse(null);

        if (nearest == null) {
            return null;
        }

        int dday = (int) java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), nearest.getScheduledAt().toLocalDate());

        return new GuardianGoalHomeResponse.HospitalInfo(
                nearest.getId(),
                dday,
                nearest.getScheduledAt(),
                nearest.getScheduleName(),
                nearest.getPlaceAddress()
        );
    }

    private Member getMember(Long memberId) {
        if (memberId == null) {
            // 보호자의 경우 첫 번째 연결된 시니어 반환
            Member currentMember = memberUtil.getCurrentMember();
            List<FamilyConnection> connections = familyConnectionRepository
                    .findAllByGuardianIdWithSeniorAndMember(currentMember.getId());

            if (connections.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "연결된 부모님이 없습니다.");
            }

            return connections.get(0).getSenior().getMember();
        }

        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 사용자입니다."));
    }
}
