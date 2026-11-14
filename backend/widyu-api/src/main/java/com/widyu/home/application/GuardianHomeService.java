package com.widyu.home.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.MemberType;
import com.widyu.goal.healthschedule.dto.response.TodayHealthScheduleItem;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.application.TodayMedication;
import com.widyu.goal.medicineschedule.application.TodayMedicationQuery;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import com.widyu.home.dto.response.GuardianSeniorListResponse;
import com.widyu.location.realtime.application.LocationQueryService;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.walk.Walk;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianHomeService {

    private final MemberUtil memberUtil;
    private final MemberRepository memberRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final HeartRateResultRepository heartRateResultRepository;
    private final TodayMedicationQuery todayMedicationQuery;
    private final HealthScheduleRepository healthScheduleRepository;
    private final WalkRepository walkRepository;
    private final HomeAlbumRecommendationService albumRecommendationService;
    private final LocationQueryService locationQueryService;

    /**
     * 위치 열람 기록은 이 읽기 트랜잭션이 끝난 뒤 {@link GuardianHomeFacade}가 남긴다. 읽기 안에서 기록하면 요청 하나가
     * 커넥션 두 개를 쥔다(LLD-0152). 그래서 외출 상태를 읽은 보호자와 시니어를 카드와 함께 돌려준다.
     */
    public GuardianHomeCards getHomeCards(Long memberId) {
        return getHomeCards(memberId, LocalDateTime.now());
    }

    GuardianHomeCards getHomeCards(Long memberId, LocalDateTime now) {
        Member guardian = memberUtil.getCurrentMember();
        if (guardian.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "보호자만 접근 가능합니다.");
        }

        Member senior = resolveSenior(memberId, guardian);
        LocalDate today = now.toLocalDate();

        return new GuardianHomeCards(guardian.getId(), senior.getId(), GuardianHomeCardsResponse.of(
                locationQueryService.getOutingStatus(senior.getId()),
                getHeartRateInfo(senior),
                getMedicineInfo(senior, today),
                getLatestAlbums(senior),
                getHealthScheduleInfo(senior, now),
                getTodayHealthSchedules(senior, now),
                getWalkInfo(senior, today)
        ));
    }

    public GuardianSeniorListResponse getFamilySeniors() {
        Member guardian = memberUtil.getCurrentMember();
        if (guardian.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "보호자만 접근 가능합니다.");
        }

        return familyMembershipRepository.findByGuardianId(guardian.getId())
                .map(membership -> seniorProfileRepository.findAllByFamilyIdWithMember(membership.getFamily().getId()))
                .map(GuardianSeniorListResponse::from)
                .orElseGet(GuardianSeniorListResponse::empty);
    }

    private Member resolveSenior(Long memberId, Member guardian) {
        if (memberId != null) {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 사용자입니다."));
            if (member.getType() != MemberType.SENIOR) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "시니어 회원만 조회할 수 있습니다.");
            }
            SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "시니어 프로필이 없습니다."));
            if (!familyMembershipRepository.existsByFamilyIdAndGuardianId(
                    seniorProfile.getFamily().getId(), guardian.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어에 대한 접근 권한이 없습니다.");
            }
            return member;
        }

        FamilyMembership membership = familyMembershipRepository.findByGuardianId(guardian.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "연결된 부모님이 없습니다."));

        List<SeniorProfile> seniors = seniorProfileRepository
                .findAllByFamilyIdWithMember(membership.getFamily().getId());

        if (seniors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "연결된 부모님이 없습니다.");
        }

        return seniors.getFirst().getMember();
    }

    /** 홈 카드와, 그 카드의 외출 상태를 읽은 보호자, 시니어 회원 식별자. */
    public record GuardianHomeCards(Long guardianId, Long seniorId, GuardianHomeCardsResponse response) {
    }

    private GuardianHomeCardsResponse.HeartRateInfo getHeartRateInfo(Member senior) {
        return heartRateResultRepository.findByMemberId(senior.getId())
                .map(GuardianHomeCardsResponse.HeartRateInfo::from)
                .orElse(GuardianHomeCardsResponse.HeartRateInfo.unknown());
    }

    private GuardianHomeCardsResponse.MedicineInfo getMedicineInfo(Member senior, LocalDate today) {
        TodayMedication todayMedication = todayMedicationQuery.find(senior, today);
        List<MedicineSchedule> schedules = todayMedication.schedules();

        if (schedules.isEmpty()) {
            return null;
        }

        Map<Long, MedicationProof> proofsByScheduleId = todayMedication.proofsByScheduleId();
        Set<Long> takenIds = proofsByScheduleId.keySet();

        // 오늘 유효한 일정의 대표 인증(일정별 최초 인증) 중 가장 최근 것이다. 시니어 화면과 같은 규칙이다 (#1059).
        String latestProofImageUrl = proofsByScheduleId.values().stream()
                .max(Comparator.comparing(MedicationProof::getVerifiedAt))
                .map(MedicationProof::getProofImageUrls)
                .filter(urls -> !urls.isEmpty())
                .map(List::getFirst)
                .orElse(null);

        List<GuardianHomeCardsResponse.ScheduleStatus> scheduleStatuses = schedules.stream()
                .sorted(Comparator.comparing(MedicineSchedule::getAlarmTime))
                .map(s -> GuardianHomeCardsResponse.ScheduleStatus.from(s, takenIds.contains(s.getId())))
                .toList();

        int totalCount = schedules.size();

        return GuardianHomeCardsResponse.MedicineInfo.of(totalCount, latestProofImageUrl, scheduleStatuses);
    }

    private List<GuardianHomeCardsResponse.AlbumInfo> getLatestAlbums(Member senior) {
        return albumRecommendationService.findLatestAlbums(senior).stream()
                .map(GuardianHomeCardsResponse.AlbumInfo::from)
                .toList();
    }

    private GuardianHomeCardsResponse.HealthScheduleInfo getHealthScheduleInfo(Member senior, LocalDateTime now) {
        return healthScheduleRepository
                .findFirstByMemberIdAndScheduledAtAfterOrderByScheduledAtAsc(senior.getId(), now)
                .map(GuardianHomeCardsResponse.HealthScheduleInfo::from)
                .orElse(null);
    }

    // 다음 일정 조회는 기간 제한이 없어 오늘 목록은 [오늘 00:00, 내일 00:00) 조회를 하나 더 쓴다(LLD-0107).
    private List<TodayHealthScheduleItem> getTodayHealthSchedules(Member senior, LocalDateTime now) {
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        return TodayHealthScheduleItem.listFrom(
                healthScheduleRepository.findByMemberIdAndDate(senior.getId(), todayStart, todayStart.plusDays(1)),
                now);
    }

    private GuardianHomeCardsResponse.WalkInfo getWalkInfo(Member senior, LocalDate today) {
        Optional<Walk> walk = walkRepository.findByMemberAndWalkDate(senior, today);

        if (walk.isPresent()) {
            return GuardianHomeCardsResponse.WalkInfo.from(walk.get());
        }

        SeniorProfile seniorProfile = senior.getSeniorProfile();
        if (seniorProfile != null && seniorProfile.hasDefaultWalkGoal()) {
            return GuardianHomeCardsResponse.WalkInfo.withDefaultGoal(seniorProfile.getDefaultWalkGoal());
        }

        return null;
    }
}
