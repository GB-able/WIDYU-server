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
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleTodayResponse;
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

    public MedicineScheduleTodayResponse getTodaySchedules(Long memberId) {
        Member targetMember = getMember(memberId);

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(targetMember, Status.ACTIVE);

        List<MedicineScheduleTodayResponse.ScheduleItem> scheduleItems = schedules.stream()
                .map(schedule -> new MedicineScheduleTodayResponse.ScheduleItem(
                        schedule.getId(),
                        schedule.getTotalCount(),
                        schedule.getAlarmTime().toString(),
                        schedule.getCategories().stream()
                                .flatMap(category -> category.getMedicines().stream()
                                        .map(detail -> new MedicineScheduleTodayResponse.MedicineItem(
                                                detail.getMedicine().getName(),
                                                detail.getDose()
                                        )))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        return new MedicineScheduleTodayResponse(scheduleItems);
    }

    public MedicineMonthlyResponse getMonthlyStats(int year, int month, Long memberId) {
        Member targetMember = getMember(memberId);
        YearMonth requestedMonth = YearMonth.of(year, month);
        YearMonth previousMonth = requestedMonth.minusMonths(1);

        int lastMonthCount = countAchievedInMonth(targetMember.getId(), previousMonth);
        int currentMonthCount = countAchievedInMonth(targetMember.getId(), requestedMonth);

        List<Double> monthlyGoalRates = calculateMonthlyGoalRates(targetMember.getId(), requestedMonth);

        return new MedicineMonthlyResponse(lastMonthCount, currentMonthCount, monthlyGoalRates);
    }

    public MedicineHomeResponse getHomeSchedules(Long memberId) {
        Member targetMember = getMember(memberId);

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(targetMember, Status.ACTIVE);

        List<MedicineHomeResponse.ScheduleItem> scheduleItems = schedules.stream()
                .map(schedule -> new MedicineHomeResponse.ScheduleItem(
                        schedule.getId(),
                        schedule.getTotalCount(),
                        schedule.getAlarmTime().toString(),
                        schedule.getCategories().stream()
                                .flatMap(category -> category.getMedicines().stream()
                                        .map(detail -> new MedicineHomeResponse.MedicineDetail(
                                                detail.getMedicine().getName(),
                                                detail.getDose()
                                        )))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        return new MedicineHomeResponse(scheduleItems);
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

        List<MedicineScheduleDetailResponse.CategoryItem> categories = schedule.getCategories().stream()
                .map(category -> new MedicineScheduleDetailResponse.CategoryItem(
                        category.getId(),
                        category.getName(),
                        category.getCountSum().doubleValue(),
                        category.getMedicines().stream()
                                .map(detail -> new MedicineScheduleDetailResponse.MedicineItem(
                                        detail.getMedicine().getId(),
                                        detail.getMedicine().getName(),
                                        detail.getDose().doubleValue(),
                                        detail.getMedicine().getImageUrl(),
                                        detail.getMedicine().getDescription()
                                ))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        return new MedicineScheduleDetailResponse(
                schedule.getAlarmTime().toString(),
                schedule.getTotalCount().doubleValue(),
                categories
        );
    }

    @Transactional
    public MedicineScheduleIdResponse createSchedule(CreateMedicineScheduleRequest request, Long memberId) {
        Member targetMember = getMember(memberId);

        LocalTime alarmTime = LocalTime.parse(request.alarmTime());
        MedicineSchedule schedule = MedicineSchedule.create(targetMember, alarmTime);

        for (CreateMedicineScheduleRequest.CategoryItem categoryItem : request.categories()) {
            MedicineCategory category = MedicineCategory.create(categoryItem.name());
            schedule.addCategory(category);

            for (CreateMedicineScheduleRequest.MedicineItem medicineItem : categoryItem.medicines()) {
                Medicine medicine = findOrCreateMedicine(medicineItem);

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

        LocalTime alarmTime = LocalTime.parse(request.alarmTime());
        schedule.updateAlarmTime(alarmTime);

        schedule.getCategories().clear();

        for (UpdateMedicineScheduleRequest.CategoryItem categoryItem : request.categories()) {
            MedicineCategory category = MedicineCategory.create(categoryItem.name());
            schedule.addCategory(category);

            for (UpdateMedicineScheduleRequest.MedicineItem medicineItem : categoryItem.medicines()) {
                Medicine medicine = findOrCreateMedicine(medicineItem);

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

    private Medicine findOrCreateMedicine(CreateMedicineScheduleRequest.MedicineItem medicineItem) {
        if (medicineItem.itemSeq() != null && !medicineItem.itemSeq().isBlank()) {
            return medicineRepository.findByItemSeq(medicineItem.itemSeq())
                    .orElseGet(() -> {
                        Medicine newMedicine = Medicine.create(
                                medicineItem.itemSeq(),
                                medicineItem.itemName(),
                                medicineItem.entpName(),
                                medicineItem.itemImage(),
                                medicineItem.useMethodQesitm(),
                                medicineItem.efcyQesitm()
                        );
                        return medicineRepository.save(newMedicine);
                    });
        }

        // itemSeq가 없으면 이름으로 검색 (기존 약품 재사용)
        return medicineRepository.findByItemName(medicineItem.itemName())
                .orElseGet(() -> {
                    Medicine newMedicine = Medicine.create(
                            null,
                            medicineItem.itemName(),
                            medicineItem.entpName(),
                            medicineItem.itemImage(),
                            medicineItem.useMethodQesitm(),
                            medicineItem.efcyQesitm()
                    );
                    return medicineRepository.save(newMedicine);
                });
    }

    private Medicine findOrCreateMedicine(UpdateMedicineScheduleRequest.MedicineItem medicineItem) {
        if (medicineItem.itemSeq() != null && !medicineItem.itemSeq().isBlank()) {
            return medicineRepository.findByItemSeq(medicineItem.itemSeq())
                    .orElseGet(() -> {
                        Medicine newMedicine = Medicine.create(
                                medicineItem.itemSeq(),
                                medicineItem.itemName(),
                                medicineItem.entpName(),
                                medicineItem.itemImage(),
                                medicineItem.useMethodQesitm(),
                                medicineItem.efcyQesitm()
                        );
                        return medicineRepository.save(newMedicine);
                    });
        }

        // itemSeq가 없으면 이름으로 검색 (기존 약품 재사용)
        return medicineRepository.findByItemName(medicineItem.itemName())
                .orElseGet(() -> {
                    Medicine newMedicine = Medicine.create(
                            null,
                            medicineItem.itemName(),
                            medicineItem.entpName(),
                            medicineItem.itemImage(),
                            medicineItem.useMethodQesitm(),
                            medicineItem.efcyQesitm()
                    );
                    return medicineRepository.save(newMedicine);
                });
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

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 사용자입니다."));

        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusOrderByAlarmTime(member, Status.ACTIVE);

        int totalSchedulesPerDay = schedules.size();

        if (totalSchedulesPerDay == 0) {
            for (int i = 0; i < daysInMonth; i++) {
                rates.add(0.0);
            }
            return rates;
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            int achievedCount = 0;

            for (MedicineSchedule schedule : schedules) {
                boolean hasProof = medicationProofRepository.existsByMedicineScheduleAndVerifiedAtBetween(
                        schedule,
                        date.atStartOfDay(),
                        date.atTime(LocalTime.MAX)
                );
                if (hasProof) {
                    achievedCount++;
                }
            }

            double rate = (double) achievedCount / totalSchedulesPerDay;
            rates.add(rate);
        }

        return rates;
    }
}
