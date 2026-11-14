package com.widyu.home.application;

import com.widyu.album.application.AlbumPermissionService;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.dto.response.TodayHealthScheduleItem;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.application.TodayMedication;
import com.widyu.goal.medicineschedule.application.TodayMedicationQuery;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.dto.response.SeniorHomeCardsResponse;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.walk.Walk;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorHomeService {

    private final MemberUtil memberUtil;
    private final TodayMedicationQuery todayMedicationQuery;
    private final WalkRepository walkRepository;
    private final HealthScheduleRepository healthScheduleRepository;
    private final HeartRateResultRepository heartRateResultRepository;
    private final HomeAlbumRecommendationService albumRecommendationService;
    private final AlbumPermissionService albumPermissionService;

    public SeniorHomeCardsResponse getHomeCards() {
        return getHomeCards(LocalDateTime.now());
    }

    SeniorHomeCardsResponse getHomeCards(LocalDateTime now) {
        Member member = memberUtil.getCurrentMember();

        if (member.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "시니어만 접근 가능합니다.");
        }

        LocalDate today = now.toLocalDate();

        // 오늘 00:00부터 1개월 이내 일정을 한 번에 조회해 다음 일정과 오늘 목록을 같은 now로 만든다(LLD-0107).
        List<HealthSchedule> healthSchedules = healthScheduleRepository
                .findByMemberIdAndWeek(member.getId(), today.atStartOfDay(), now.plusMonths(1));

        return SeniorHomeCardsResponse.of(
                getHeartRateInfo(member),
                getMedicineInfo(member, now),
                getScoredAlbums(member, today),
                getHealthScheduleInfo(healthSchedules, now),
                TodayHealthScheduleItem.listFrom(healthSchedules, now),
                getWalkInfo(member, today)
        );
    }

    private SeniorHomeCardsResponse.HeartRateInfo getHeartRateInfo(Member member) {
        return heartRateResultRepository.findByMemberId(member.getId())
                .map(SeniorHomeCardsResponse.HeartRateInfo::from)
                .orElse(SeniorHomeCardsResponse.HeartRateInfo.unknown());
    }

    private SeniorHomeCardsResponse.MedicineInfo getMedicineInfo(Member member, LocalDateTime now) {
        TodayMedication todayMedication = todayMedicationQuery.find(member, now.toLocalDate());
        List<MedicineSchedule> schedules = todayMedication.schedules();

        if (schedules.isEmpty()) {
            return null;
        }

        Map<Long, MedicationProof> proofsByScheduleId = todayMedication.proofsByScheduleId();

        List<SeniorHomeCardsResponse.ScheduleStatus> scheduleStatuses = schedules.stream()
                .sorted(Comparator.comparing(MedicineSchedule::getAlarmTime))
                .map(s -> SeniorHomeCardsResponse.ScheduleStatus.from(s, proofsByScheduleId.containsKey(s.getId())))
                .toList();

        MedicineSchedule nextSchedule = todayMedication.nextSchedule(now.toLocalTime());

        return SeniorHomeCardsResponse.MedicineInfo.from(
                nextSchedule,
                proofsByScheduleId.size(),
                schedules.size(),
                scheduleStatuses,
                proofsByScheduleId.get(nextSchedule.getId())
        );
    }

    private List<SeniorHomeCardsResponse.AlbumInfo> getScoredAlbums(Member senior, LocalDate today) {
        return albumRecommendationService.recommendAlbums(senior, today).stream()
                .map(album -> SeniorHomeCardsResponse.AlbumInfo.from(
                        album, albumPermissionService.isUnlockedFor(album, senior)))
                .toList();
    }

    private SeniorHomeCardsResponse.HealthScheduleInfo getHealthScheduleInfo(
            List<HealthSchedule> schedules, LocalDateTime now) {
        Optional<HealthSchedule> nearest = schedules.stream()
                .filter(s -> s.getScheduledAt().isAfter(now))
                .min(Comparator.comparing(HealthSchedule::getScheduledAt));

        if (nearest.isEmpty()) {
            return null;
        }

        return SeniorHomeCardsResponse.HealthScheduleInfo.from(nearest.get(), now.toLocalDate());
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
