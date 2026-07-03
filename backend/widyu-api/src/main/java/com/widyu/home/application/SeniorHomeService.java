package com.widyu.home.application;

import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.dto.response.SeniorHomeCardsResponse;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.walk.Walk;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorHomeService {

    private final MemberUtil memberUtil;
    private final MedicineScheduleRepository medicineScheduleRepository;
    private final MedicationProofRepository medicationProofRepository;
    private final WalkRepository walkRepository;
    private final HealthScheduleRepository healthScheduleRepository;
    private final HeartRateResultRepository heartRateResultRepository;
    private final HomeAlbumRecommendationService albumRecommendationService;

    public SeniorHomeCardsResponse getHomeCards() {
        Member member = memberUtil.getCurrentMember();

        if (member.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "시니어만 접근 가능합니다.");
        }

        LocalDate today = LocalDate.now();

        return new SeniorHomeCardsResponse(
                getHeartRateInfo(member),
                getMedicineInfo(member, today),
                getScoredAlbums(member, today),
                getHealthScheduleInfo(member, today),
                getWalkInfo(member, today)
        );
    }

    private SeniorHomeCardsResponse.HeartRateInfo getHeartRateInfo(Member member) {
        return heartRateResultRepository.findByMemberId(member.getId())
                .map(SeniorHomeCardsResponse.HeartRateInfo::from)
                .orElse(SeniorHomeCardsResponse.HeartRateInfo.unknown());
    }

    private SeniorHomeCardsResponse.MedicineInfo getMedicineInfo(Member member, LocalDate today) {
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(member, Status.ACTIVE);

        if (schedules.isEmpty()) {
            return null;
        }

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        Set<Long> takenScheduleIds = medicationProofRepository
                .findByMemberIdAndDateRange(member.getId(), startOfDay, endOfDay)
                .stream()
                .map(proof -> proof.getMedicineSchedule().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<SeniorHomeCardsResponse.ScheduleStatus> scheduleStatuses = schedules.stream()
                .sorted(Comparator.comparing(MedicineSchedule::getAlarmTime))
                .map(s -> SeniorHomeCardsResponse.ScheduleStatus.from(s, takenScheduleIds.contains(s.getId())))
                .toList();

        MedicineSchedule nextSchedule = findNextSchedule(schedules);

        return SeniorHomeCardsResponse.MedicineInfo.from(
                nextSchedule,
                takenScheduleIds.size(),
                schedules.size(),
                scheduleStatuses
        );
    }

    private MedicineSchedule findNextSchedule(List<MedicineSchedule> schedules) {
        LocalTime now = LocalTime.now();
        return schedules.stream()
                .filter(s -> s.getAlarmTime().isAfter(now))
                .min(Comparator.comparing(MedicineSchedule::getAlarmTime))
                .orElse(schedules.getFirst());
    }

    private List<SeniorHomeCardsResponse.AlbumInfo> getScoredAlbums(Member senior, LocalDate today) {
        return albumRecommendationService.recommendAlbums(senior, today).stream()
                .map(SeniorHomeCardsResponse.AlbumInfo::from)
                .toList();
    }

    private SeniorHomeCardsResponse.HealthScheduleInfo getHealthScheduleInfo(Member member, LocalDate today) {
        LocalDateTime now = LocalDateTime.now();

        Optional<HealthSchedule> nearest = healthScheduleRepository
                .findByMemberIdAndWeek(member.getId(), now, now.plusMonths(1))
                .stream()
                .filter(s -> s.getScheduledAt().isAfter(now))
                .min(Comparator.comparing(HealthSchedule::getScheduledAt));

        if (nearest.isEmpty()) {
            return null;
        }

        return SeniorHomeCardsResponse.HealthScheduleInfo.from(nearest.get(), today);
    }

    private SeniorHomeCardsResponse.WalkInfo getWalkInfo(Member member, LocalDate today) {
        Optional<Walk> walk = walkRepository.findByMemberAndWalkDate(member, today);

        if (walk.isPresent()) {
            return SeniorHomeCardsResponse.WalkInfo.from(walk.get());
        }

        SeniorProfile seniorProfile = member.getSeniorProfile();
        if (seniorProfile != null && seniorProfile.hasDefaultWalkGoal()) {
            return SeniorHomeCardsResponse.WalkInfo.withDefaultGoal(seniorProfile.getDefaultWalkGoal());
        }

        return null;
    }
}
