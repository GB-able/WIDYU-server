package com.widyu.goal.medicineschedule.application;

import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.goal.medicineschedule.dto.request.CreateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.request.UpdateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.response.MedicineHomeResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineMonthlyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDetailResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleChangeResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleIdResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicationStatus;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDailyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineSearchResponse;
import com.widyu.goal.rate.GoalRateCalculator;
import com.widyu.goal.rate.GoalTally;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.Medicine;
import com.widyu.medicine.MedicineCategory;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.medicine.MedicineScheduleDetail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicineScheduleService {
    private final MedicineScheduleRepository medicineScheduleRepository;
    private final MedicineRepository medicineRepository;
    private final MedicationProofRepository medicationProofRepository;
    private final MemberRepository memberRepository;
    private final MemberUtil memberUtil;
    private final FcmService fcmService;
    static final int MAX_ALARMS_PER_MEMBER = 6;
    // 알람이 오늘 끝났는지 판정하는 기준 시각. 테스트가 시각을 고정할 수 있게 필드로 둔다.
    private Clock clock = Clock.systemDefaultZone();

    public MedicineScheduleDailyResponse getDailySchedules(Long memberId, LocalDate date) {
        Member targetMember = getMember(memberId);

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findEffectiveByMemberAndDateWithDetails(targetMember, Status.ACTIVE, date);

        if (schedules.isEmpty()) {
            return MedicineScheduleDailyResponse.of(List.of());
        }

        Map<Long, MedicationProof> proofsByScheduleId = findProofsByScheduleId(targetMember, date);
        LocalDateTime now = LocalDateTime.now(clock);

        List<MedicineScheduleDailyResponse.ScheduleItem> scheduleItems = schedules.stream()
                .map(schedule -> {
                    MedicationProof proof = proofsByScheduleId.get(schedule.getId());
                    boolean verified = proof != null;
                    MedicationStatus status = MedicationStatus.of(verified, date, schedule.getAlarmTime(), now);
                    String proofImageUrl = extractProofImageUrl(proof);
                    return MedicineScheduleDailyResponse.ScheduleItem.from(schedule, status, proofImageUrl);
                })
                .collect(Collectors.toList());

        return MedicineScheduleDailyResponse.of(scheduleItems);
    }

    private Map<Long, MedicationProof> findProofsByScheduleId(Member member, LocalDate date) {
        return medicationProofRepository.findByMemberIdAndDateRange(member.getId(), date, date)
                .stream()
                .collect(Collectors.toMap(
                        proof -> proof.getMedicineSchedule().getId(),
                        proof -> proof,
                        (existing, duplicate) -> existing
                ));
    }

    private String extractProofImageUrl(MedicationProof proof) {
        if (proof == null) {
            return null;
        }

        if (proof.getProofImageUrls().isEmpty()) {
            return null;
        }

        return proof.getProofImageUrls().get(0);
    }

    public MedicineMonthlyResponse getMonthlyStats(int year, int month, Long memberId) {
        Member targetMember = getMember(memberId);
        YearMonth requestedMonth = YearMonth.of(year, month);
        YearMonth previousMonth = requestedMonth.minusMonths(1);

        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth leftMonth = GoalRateCalculator.comparisonLeftMonth(requestedMonth, currentMonth);
        // 같은 달을 두 번 조회하지 않는다. 요청 달·전달은 기존 필드, 왼쪽 달·이번 달은 비교 박스에 쓴다.
        Map<YearMonth, List<GoalTally>> dosesByMonth = new HashMap<>();
        for (YearMonth neededMonth : List.of(requestedMonth, previousMonth, leftMonth, currentMonth)) {
            dosesByMonth.computeIfAbsent(neededMonth, key -> calculateDailyDoses(targetMember.getId(), key));
        }
        List<GoalTally> requestedDays = dosesByMonth.get(requestedMonth);

        List<Double> monthlyGoalRates = requestedDays.stream()
                .map(day -> GoalRateCalculator.toRatio(GoalRateCalculator.percent(day)))
                .toList();
        int lastMonthCount = countFullyAchievedDays(dosesByMonth.get(previousMonth));
        int currentMonthCount = countFullyAchievedDays(requestedDays);

        MedicineMonthlyResponse.MonthComparison comparison = MedicineMonthlyResponse.MonthComparison.of(
                MedicineMonthlyResponse.MonthProgress.of(leftMonth,
                        sumUntil(dosesByMonth.get(leftMonth), leftMonth, today)),
                MedicineMonthlyResponse.MonthProgress.of(currentMonth,
                        sumUntil(dosesByMonth.get(currentMonth), currentMonth, today)));

        return MedicineMonthlyResponse.of(lastMonthCount, currentMonthCount, monthlyGoalRates, comparison);
    }

    /** 그 달 1일부터 오늘까지(지난 달은 말일까지) 복용 횟수 합계. 이번 달 이후 날짜는 세지 않는다. */
    private GoalTally sumUntil(List<GoalTally> dailyDoses, YearMonth month, LocalDate today) {
        GoalTally sum = GoalTally.NONE;
        for (int day = 1; day <= dailyDoses.size(); day++) {
            if (month.atDay(day).isAfter(today)) {
                break;
            }
            sum = sum.plus(dailyDoses.get(day - 1));
        }
        return sum;
    }

    public MedicineHomeResponse getHomeSchedules(Long memberId) {
        Member targetMember = getMember(memberId);

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findEffectiveByMemberAndDateWithDetails(targetMember, Status.ACTIVE,
                        LocalDate.now(clock));

        List<MedicineHomeResponse.ScheduleItem> scheduleItems = schedules.stream()
                .map(MedicineHomeResponse.ScheduleItem::from)
                .collect(Collectors.toList());

        return MedicineHomeResponse.of(scheduleItems);
    }

    public MedicineScheduleDetailResponse getScheduleDetail(Long scheduleId, Long memberId) {
        Member targetMember = getMember(memberId);

        MedicineSchedule schedule = medicineScheduleRepository
                .findByIdAndStatusWithDetails(scheduleId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 약 복용 스케줄입니다."));

        if (!schedule.getMember().getId().equals(targetMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "해당 스케줄에 접근할 권한이 없습니다.");
        }

        return MedicineScheduleDetailResponse.from(schedule);
    }

    @Transactional
    public MedicineScheduleIdResponse createSchedule(CreateMedicineScheduleRequest request, Long memberId) {
        Member targetMember = getMemberForAlarmChange(memberId);
        LocalTime alarmTime = parseAlarmTime(request.alarmTime());
        // 회원 행 잠금 뒤에 확인하므로 같은 시각 동시 생성도 하나만 남는다.
        // 개수 상한보다 먼저 봐서, 알람이 6개여도 같은 시각이면 더 구체적인 409를 돌려준다.
        if (medicineScheduleRepository.existsByMemberAndStatusAndAlarmTimeAndEffectiveToIsNull(
                targetMember, Status.ACTIVE, alarmTime)) {
            throw new BusinessException(ErrorCode.MEDICINE_SCHEDULE_TIME_DUPLICATED);
        }
        // 회원 행 잠금 뒤에 세므로 동시에 생성해도 상한을 넘지 않는다.
        if (medicineScheduleRepository.countByMemberAndStatusAndEffectiveToIsNull(targetMember, Status.ACTIVE)
                >= MAX_ALARMS_PER_MEMBER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "약 알람은 최대 " + MAX_ALARMS_PER_MEMBER + "개까지 등록할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);

        // 오늘 인증 가능 시간이 이미 끝난 시각이면 오늘은 건너뛰고 내일부터 울린다.
        LocalDate effectiveFromDate = now.toLocalDate();
        if (isProofWindowClosed(alarmTime, now)) {
            effectiveFromDate = effectiveFromDate.plusDays(1);
        }
        MedicineSchedule schedule = MedicineSchedule.create(targetMember, alarmTime, effectiveFromDate);

        for (CreateMedicineScheduleRequest.CategoryItem categoryItem : request.categories()) {
            MedicineCategory category = MedicineCategory.create(categoryItem.name());
            schedule.addCategory(category);

            for (CreateMedicineScheduleRequest.MedicineItem medicineItem : categoryItem.medicines()) {
                Medicine medicine = findMedicineByName(medicineItem.itemName());

                MedicineScheduleDetail detail = MedicineScheduleDetail.create(
                        medicine,
                        medicineItem.dose().intValue()
                );
                category.addMedicine(detail);
            }
        }

        MedicineSchedule savedSchedule = medicineScheduleRepository.save(schedule);
        long revision = sendAlarmChanged(targetMember, NotificationType.MEDICATION_SCHEDULE_CREATED,
                savedSchedule.getId(), effectiveFromDate);
        log.info("약 복용 스케줄 생성: memberId={}, scheduleId={}",
                targetMember.getId(), savedSchedule.getId());

        return MedicineScheduleIdResponse.of(savedSchedule.getId(), revision, effectiveFromDate);
    }

    @Transactional
    public MedicineScheduleIdResponse updateSchedule(Long scheduleId, UpdateMedicineScheduleRequest request,
                                                     Long memberId) {
        Member targetMember = getMemberForAlarmChange(memberId);

        MedicineSchedule schedule = medicineScheduleRepository
                .findByIdAndStatusWithDetails(scheduleId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 약 복용 스케줄입니다."));

        if (!schedule.getMember().getId().equals(targetMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "해당 스케줄을 수정할 권한이 없습니다.");
        }

        if (!schedule.isCurrent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "이미 종료된 과거 스케줄은 수정할 수 없습니다. 최신 스케줄을 수정해주세요.");
        }

        LocalTime alarmTime = parseAlarmTime(request.alarmTime());
        // 시각을 바꿀 때만 본다. 이 규칙 전에 같은 시각 알람이 둘 저장된 회원도 약만 고치거나 정리할 수 있다.
        if (!alarmTime.equals(schedule.getAlarmTime())
                && medicineScheduleRepository.existsByMemberAndStatusAndAlarmTimeAndEffectiveToIsNull(
                        targetMember, Status.ACTIVE, alarmTime)) {
            throw new BusinessException(ErrorCode.MEDICINE_SCHEDULE_TIME_DUPLICATED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // 오늘 끝난 알람(인증 완료 또는 인증 가능 시간 종료)은 오늘 기록을 그대로 두고 내일부터 바꾼다.
        // 아직 시작 전인 버전은 원래 시작일을 유지한다.
        LocalDate effectiveFromDate = today;
        if (!schedule.isEffectiveOn(today)) {
            effectiveFromDate = schedule.getEffectiveFrom();
        } else if (isFinishedToday(schedule, now)) {
            effectiveFromDate = today.plusDays(1);
        }
        LocalDate lastDateOfPrevious = effectiveFromDate.minusDays(1);

        if (!schedule.isEffectiveOn(lastDateOfPrevious)) {
            // 적용일 전날에 유효하지 않았던 버전은 지켜야 할 날짜가 없으므로 그대로 수정한다.
            schedule.updateAlarmTime(alarmTime);
            schedule.clearCategories();
            addCategories(schedule, request.categories());
            long revision = sendAlarmChanged(targetMember, NotificationType.MEDICATION_SCHEDULE_CHANGED,
                    scheduleId, effectiveFromDate);
            log.info("약 복용 스케줄 수정(같은 버전): scheduleId={}, memberId={}, effectiveFrom={}",
                    scheduleId, targetMember.getId(), effectiveFromDate);
            return MedicineScheduleIdResponse.of(scheduleId, revision, effectiveFromDate);
        }

        schedule.closeAsOf(lastDateOfPrevious);

        MedicineSchedule newSchedule = MedicineSchedule.create(targetMember, alarmTime, effectiveFromDate);
        addCategories(newSchedule, request.categories());
        MedicineSchedule savedSchedule = medicineScheduleRepository.save(newSchedule);
        long revision = sendAlarmChanged(targetMember, NotificationType.MEDICATION_SCHEDULE_CHANGED,
                savedSchedule.getId(), effectiveFromDate);

        log.info("약 복용 스케줄 수정(새 버전 생성): oldScheduleId={}, newScheduleId={}, memberId={}, effectiveFrom={}",
                scheduleId, savedSchedule.getId(), targetMember.getId(), effectiveFromDate);
        return MedicineScheduleIdResponse.of(savedSchedule.getId(), revision, effectiveFromDate);
    }

    private boolean isFinishedToday(MedicineSchedule schedule, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (isProofWindowClosed(schedule.getAlarmTime(), now)) {
            return true;
        }
        return medicationProofRepository.existsByMedicineScheduleAndVerifiedDate(schedule, today);
    }

    private boolean isProofWindowClosed(LocalTime alarmTime, LocalDateTime now) {
        LocalDateTime windowEnd = now.toLocalDate().atTime(alarmTime)
                .plusMinutes(MedicationStatus.ALLOWED_WINDOW_MINUTES);
        return now.isAfter(windowEnd);
    }

    private void addCategories(
            MedicineSchedule schedule,
            List<UpdateMedicineScheduleRequest.CategoryItem> categoryItems
    ) {
        for (UpdateMedicineScheduleRequest.CategoryItem categoryItem : categoryItems) {
            MedicineCategory category = MedicineCategory.create(categoryItem.name());
            schedule.addCategory(category);

            for (UpdateMedicineScheduleRequest.MedicineItem medicineItem : categoryItem.medicines()) {
                Medicine medicine = findMedicineByName(medicineItem.itemName());

                MedicineScheduleDetail detail = MedicineScheduleDetail.create(
                        medicine,
                        medicineItem.dose().intValue()
                );
                category.addMedicine(detail);
            }
        }
    }

    @Transactional
    public MedicineScheduleChangeResponse deleteSchedule(Long scheduleId, Long memberId) {
        Member targetMember = getMemberForAlarmChange(memberId);

        MedicineSchedule schedule = medicineScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 약 복용 스케줄입니다."));

        if (!schedule.getMember().getId().equals(targetMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "해당 스케줄을 삭제할 권한이 없습니다.");
        }

        if (!schedule.isCurrent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "이미 종료된 과거 스케줄은 삭제할 수 없습니다.");
        }

        // 오늘 끝난 알람은 오늘 기록(완료·미인증)을 남기고 내일부터, 아직 안 끝난 알람은 오늘부터 중단한다.
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDate stopDate = today;
        if (schedule.isEffectiveOn(today) && isFinishedToday(schedule, now)) {
            stopDate = today.plusDays(1);
        }
        schedule.closeAsOf(stopDate.minusDays(1));
        long revision = sendAlarmChanged(targetMember, NotificationType.MEDICATION_SCHEDULE_DELETED,
                scheduleId, stopDate);
        log.info("약 복용 스케줄 삭제: scheduleId={}, memberId={}, stopDate={}",
                scheduleId, targetMember.getId(), stopDate);
        return MedicineScheduleChangeResponse.of(revision, stopDate);
    }

    private LocalTime parseAlarmTime(String alarmTimeStr) {
        try {
            return LocalTime.parse(alarmTimeStr);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "알람 시간 형식이 올바르지 않습니다. HH:mm 형식으로 입력해주세요.");
        }
    }

    private Medicine findMedicineByName(String itemName) {
        return medicineRepository.findByItemName(itemName.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "약품을 찾을 수 없습니다. 먼저 약품 검색을 통해 약품을 등록해주세요."));
    }

    private Member getMember(Long memberId) {
        if (memberId == null) {
            return memberUtil.getCurrentMember();
        }

        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 사용자입니다."));
    }

    private Member getMemberForAlarmChange(Long memberId) {
        Long targetMemberId = memberId;
        if (targetMemberId == null) {
            targetMemberId = memberUtil.getCurrentMember().getId();
        }

        return memberRepository.findByIdForUpdate(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 사용자입니다."));
    }

    private long sendAlarmChanged(Member member, NotificationType type, Long scheduleId,
                                  LocalDate effectiveFromDate) {
        long revision = member.incrementMedicationAlarmRevision();
        Member actor = memberUtil.getCurrentMember();
        String actorName = null;
        if (actor != null) {
            actorName = actor.getName();
        }
        if (actorName == null || actorName.isBlank()) {
            actorName = "가족";
        }
        String copyCode = type.copyCode();
        // 끝난 알람의 변경처럼 내일부터 적용되면 "내일부터" 문구를 쓴다(LLD-0113)
        if (effectiveFromDate.isAfter(LocalDate.now(clock))) {
            copyCode = copyCode + "-NEXT";
        }
        NotificationCopy copy = NotificationCopy.of(type, copyCode,
                Map.of("보호자 이름", actorName));
        fcmService.sendMessageToUser(member.getId(), FcmSendDto.builder()
                .title(copy.title()).content(copy.body())
                .fcmCategory(FcmCategory.MEDICINE_SCHEDULE)
                .notificationType(type).entityId(scheduleId.toString())
                .actorDisplayName(actorName).effectiveFromDate(effectiveFromDate.toString())
                .eventId(MedicationAlarmPayload.eventId(member.getId() + ":" + revision + ":" + type.name()))
                .data(MedicationAlarmPayload.of(revision, type))
                .build());
        NotificationType syncType = NotificationType.MEDICATION_SCHEDULE_SYNC;
        fcmService.sendMessageToUser(member.getId(), FcmSendDto.builder()
                .fcmCategory(FcmCategory.MEDICINE_SCHEDULE)
                .notificationType(syncType)
                .eventId(MedicationAlarmPayload.eventId(member.getId() + ":" + revision + ":" + syncType.name()))
                .data(MedicationAlarmPayload.of(revision, syncType))
                .build());
        return revision;
    }

    // 그날 유효했던 스케줄을 모두 인증한 날만 달성일로 센다
    private int countFullyAchievedDays(List<GoalTally> dailyDoses) {
        return (int) dailyDoses.stream()
                .filter(GoalTally::isComplete)
                .count();
    }

    /** 날짜별 복용 횟수(인증 수 / 그날 유효했던 스케줄 수). 1일부터 말일까지 순서대로다. */
    private List<GoalTally> calculateDailyDoses(Long memberId, YearMonth month) {
        int daysInMonth = month.lengthOfMonth();
        List<GoalTally> doses = new ArrayList<>();

        Member member = getMember(memberId);

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        // 이 달과 겹치는 모든 스케줄 버전 (날짜별 유효 수는 버전 유효 구간으로 계산한다)
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findEffectiveByMemberAndDateRange(member, Status.ACTIVE, monthStart, monthEnd);

        if (schedules.isEmpty()) {
            for (int i = 0; i < daysInMonth; i++) {
                doses.add(GoalTally.NONE);
            }
            return doses;
        }

        // 한 달치 MedicationProof를 한 번에 조회
        List<MedicationProof> proofs = medicationProofRepository
                .findByMemberIdAndDateRange(member.getId(), monthStart, monthEnd);

        // 날짜별로 "어떤 스케줄들이 인증됐는지" 집계
        Map<LocalDate, Set<Long>> scheduleIdsByDate = proofs.stream()
                .collect(Collectors.groupingBy(
                        MedicationProof::getVerifiedDate,
                        Collectors.mapping(p -> p.getMedicineSchedule().getId(), Collectors.toSet())
                ));

        // 각 날짜별 복용 횟수 (분모 = 그날 유효했던 스케줄 수)
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);

            Set<Long> effectiveScheduleIds = schedules.stream()
                    .filter(schedule -> schedule.isEffectiveOn(date))
                    .map(MedicineSchedule::getId)
                    .collect(Collectors.toSet());
            long achievedCount = scheduleIdsByDate
                    .getOrDefault(date, Set.of())
                    .stream()
                    .filter(effectiveScheduleIds::contains)
                    .count();

            doses.add(GoalTally.of(achievedCount, effectiveScheduleIds.size()));
        }

        return doses;
    }
}
