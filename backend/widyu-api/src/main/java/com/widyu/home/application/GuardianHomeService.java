package com.widyu.home.application;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.MemberType;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianHomeService {

    private static final int ALBUM_CANDIDATE_SIZE = 10;
    private static final int ALBUM_RESULT_SIZE = 3;
    private static final int LIKE_WEIGHT = 3;
    private static final int COMMENT_WEIGHT = 2;
    private static final int DATE_BONUS = 10;

    private final MemberUtil memberUtil;
    private final MemberRepository memberRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final HeartRateResultRepository heartRateResultRepository;
    private final MedicineScheduleRepository medicineScheduleRepository;
    private final MedicationProofRepository medicationProofRepository;
    private final HealthScheduleRepository healthScheduleRepository;
    private final WalkRepository walkRepository;
    private final AlbumRepository albumRepository;

    public GuardianHomeCardsResponse getHomeCards(Long memberId) {
        Member guardian = memberUtil.getCurrentMember();
        if (guardian.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "보호자만 접근 가능합니다.");
        }

        Member senior = resolveSenior(memberId, guardian);
        LocalDate today = LocalDate.now();

        return new GuardianHomeCardsResponse(
                getHeartRateInfo(senior),
                getMedicineInfo(senior, today),
                getScoredAlbums(senior, today),
                getHealthScheduleInfo(senior, today),
                getWalkInfo(senior, today)
        );
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

    private GuardianHomeCardsResponse.HeartRateInfo getHeartRateInfo(Member senior) {
        return heartRateResultRepository.findByMemberId(senior.getId())
                .map(GuardianHomeCardsResponse.HeartRateInfo::from)
                .orElse(GuardianHomeCardsResponse.HeartRateInfo.unknown());
    }

    private GuardianHomeCardsResponse.MedicineInfo getMedicineInfo(Member senior, LocalDate today) {
        List<MedicineSchedule> schedules = medicineScheduleRepository
                .findByMemberAndStatusWithDetails(senior, Status.ACTIVE);

        if (schedules.isEmpty()) {
            return null;
        }

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        Map<Long, MedicationProof> proofMap = medicationProofRepository
                .findByMemberIdAndDateRange(senior.getId(), startOfDay, endOfDay)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getMedicineSchedule().getId(),
                        p -> p,
                        (p1, p2) -> p1
                ));

        Set<Long> takenIds = proofMap.keySet();

        String latestProofImageUrl = proofMap.values().stream()
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

        return new GuardianHomeCardsResponse.MedicineInfo(totalCount, latestProofImageUrl, scheduleStatuses);
    }

    private List<GuardianHomeCardsResponse.AlbumInfo> getScoredAlbums(Member senior, LocalDate today) {
        List<Long> familyMemberIds = getFamilyMemberIds(senior);

        List<Long> candidateIds = albumRepository
                .findTopScoredAlbumIdsByMemberIds(familyMemberIds, PageRequest.of(0, ALBUM_CANDIDATE_SIZE))
                .getContent();

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        return albumRepository.findAlbumsWithCollectionsByIds(candidateIds).stream()
                .sorted(Comparator.comparingInt((Album album) -> calculateScore(album, today)).reversed())
                .limit(ALBUM_RESULT_SIZE)
                .map(GuardianHomeCardsResponse.AlbumInfo::from)
                .toList();
    }

    private List<Long> getFamilyMemberIds(Member senior) {
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(senior.getId())
                .orElse(null);

        if (seniorProfile == null) {
            return List.of(senior.getId());
        }

        Long familyId = seniorProfile.getFamily().getId();

        List<Long> seniorIds = seniorProfileRepository.findAllByFamilyId(familyId).stream()
                .map(sp -> sp.getMember().getId())
                .toList();

        List<Long> guardianIds = familyMembershipRepository.findAllByFamilyIdWithGuardian(familyId).stream()
                .map(fm -> fm.getGuardian().getId())
                .toList();

        return java.util.stream.Stream.concat(seniorIds.stream(), guardianIds.stream()).toList();
    }

    private int calculateScore(Album album, LocalDate today) {
        int baseScore = album.getLikeCount() * LIKE_WEIGHT + album.getCommentCount() * COMMENT_WEIGHT;
        if (isAnniversary(album, today)) {
            return baseScore + DATE_BONUS;
        }
        return baseScore;
    }

    private boolean isAnniversary(Album album, LocalDate today) {
        LocalDate albumDate = album.getCreatedAt().toLocalDate();
        return albumDate.getMonth() == today.getMonth()
                && albumDate.getDayOfMonth() == today.getDayOfMonth();
    }

    private GuardianHomeCardsResponse.HealthScheduleInfo getHealthScheduleInfo(Member senior, LocalDate today) {
        return healthScheduleRepository.findByMemberIdAndDate(senior.getId(), today)
                .stream()
                .min(Comparator.comparing(HealthSchedule::getScheduledAt))
                .map(GuardianHomeCardsResponse.HealthScheduleInfo::from)
                .orElse(null);
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
