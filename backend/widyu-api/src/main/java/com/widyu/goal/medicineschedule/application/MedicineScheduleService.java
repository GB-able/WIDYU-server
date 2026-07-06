package com.widyu.goal.medicineschedule.application;

import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.dto.request.CreateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.request.UpdateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.response.MedicineHomeResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineMonthlyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDetailResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleIdResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicationStatus;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDailyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineSearchResponse;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import com.widyu.medicine.Medicine;
import com.widyu.medicine.MedicineCategory;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.medicine.MedicineScheduleDetail;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
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

    public MedicineScheduleDailyResponse getDailySchedules(Long memberId, LocalDate date) {
        Member targetMember = getMember(memberId);

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(targetMember, Status.ACTIVE);

        Set<Long> verifiedScheduleIds = findVerifiedScheduleIds(schedules, date);
        LocalDateTime now = LocalDateTime.now();

        List<MedicineScheduleDailyResponse.ScheduleItem> scheduleItems = schedules.stream()
                .map(schedule -> {
                    boolean verified = verifiedScheduleIds.contains(schedule.getId());
                    MedicationStatus status = MedicationStatus.of(verified, date, schedule.getAlarmTime(), now);
                    return MedicineScheduleDailyResponse.ScheduleItem.from(schedule, status);
                })
                .collect(Collectors.toList());

        return MedicineScheduleDailyResponse.of(scheduleItems);
    }

    private Set<Long> findVerifiedScheduleIds(List<MedicineSchedule> schedules, LocalDate date) {
        List<Long> scheduleIds = schedules.stream()
                .map(MedicineSchedule::getId)
                .collect(Collectors.toList());

        if (scheduleIds.isEmpty()) {
            return Set.of();
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        return Set.copyOf(medicationProofRepository.findVerifiedScheduleIds(scheduleIds, startOfDay, endOfDay));
    }

    public MedicineMonthlyResponse getMonthlyStats(int year, int month, Long memberId) {
        Member targetMember = getMember(memberId);
        YearMonth requestedMonth = YearMonth.of(year, month);
        YearMonth previousMonth = requestedMonth.minusMonths(1);

        int lastMonthCount = countAchievedInMonth(targetMember.getId(), previousMonth);
        int currentMonthCount = countAchievedInMonth(targetMember.getId(), requestedMonth);

        List<Double> monthlyGoalRates = calculateMonthlyGoalRates(targetMember.getId(), requestedMonth);

        return MedicineMonthlyResponse.of(lastMonthCount, currentMonthCount, monthlyGoalRates);
    }

    public MedicineHomeResponse getHomeSchedules(Long memberId) {
        Member targetMember = getMember(memberId);

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(targetMember, Status.ACTIVE);

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
        Member targetMember = getMember(memberId);

        LocalTime alarmTime = parseAlarmTime(request.alarmTime());
        MedicineSchedule schedule = MedicineSchedule.create(targetMember, alarmTime);

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
        log.info("약 복용 스케줄 생성: memberId={}, scheduleId={}",
                targetMember.getId(), savedSchedule.getId());

        return MedicineScheduleIdResponse.of(savedSchedule.getId());
    }

    @Transactional
    public void updateSchedule(Long scheduleId, UpdateMedicineScheduleRequest request, Long memberId) {
        Member targetMember = getMember(memberId);

        MedicineSchedule schedule = medicineScheduleRepository
                .findByIdAndStatusWithDetails(scheduleId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 약 복용 스케줄입니다."));

        if (!schedule.getMember().getId().equals(targetMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "해당 스케줄을 수정할 권한이 없습니다.");
        }

        LocalTime alarmTime = parseAlarmTime(request.alarmTime());
        schedule.updateAlarmTime(alarmTime);

        schedule.getCategories().clear();

        for (UpdateMedicineScheduleRequest.CategoryItem categoryItem : request.categories()) {
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

        log.info("약 복용 스케줄 수정: scheduleId={}, memberId={}", scheduleId, targetMember.getId());
    }

    @Transactional
    public void deleteSchedule(Long scheduleId, Long memberId) {
        Member targetMember = getMember(memberId);

        MedicineSchedule schedule = medicineScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 약 복용 스케줄입니다."));

        if (!schedule.getMember().getId().equals(targetMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "해당 스케줄을 삭제할 권한이 없습니다.");
        }

        schedule.delete();
        log.info("약 복용 스케줄 삭제: scheduleId={}, memberId={}", scheduleId, targetMember.getId());
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

    private int countAchievedInMonth(Long memberId, YearMonth month) {
        LocalDateTime startDate = month.atDay(1).atStartOfDay();
        LocalDateTime endDate = month.atEndOfMonth().atTime(LocalTime.MAX);

        List<com.widyu.medicine.MedicationProof> proofs = medicationProofRepository
                .findByMemberIdAndDateRange(memberId, startDate, endDate);

        return (int) proofs.stream()
                .map(proof -> proof.getVerifiedAt().toLocalDate())
                .distinct()
                .count();
    }

    private List<Double> calculateMonthlyGoalRates(Long memberId, YearMonth month) {
        int daysInMonth = month.lengthOfMonth();
        List<Double> rates = new ArrayList<>();

        Member member = getMember(memberId);

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusOrderByAlarmTime(member, Status.ACTIVE);

        int totalSchedulesPerDay = schedules.size();

        if (totalSchedulesPerDay == 0) {
            for (int i = 0; i < daysInMonth; i++) {
                rates.add(0.0);
            }
            return rates;
        }

        // 한 달치 MedicationProof를 한 번에 조회
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(LocalTime.MAX);
        List<com.widyu.medicine.MedicationProof> proofs = medicationProofRepository
                .findByMemberIdAndDateRange(member.getId(), start, end);

        // 날짜별로 "어떤 스케줄들이 인증됐는지" 집계
        Map<LocalDate, Set<Long>> scheduleIdsByDate = proofs.stream()
                .collect(Collectors.groupingBy(
                        proof -> proof.getVerifiedAt().toLocalDate(),
                        Collectors.mapping(p -> p.getMedicineSchedule().getId(), Collectors.toSet())
                ));

        // 각 날짜별 달성률 계산
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            int achievedCount = scheduleIdsByDate
                    .getOrDefault(date, Set.of())
                    .size();

            double rate = (double) achievedCount / totalSchedulesPerDay;
            rates.add(rate);
        }

        return rates;
    }
}
