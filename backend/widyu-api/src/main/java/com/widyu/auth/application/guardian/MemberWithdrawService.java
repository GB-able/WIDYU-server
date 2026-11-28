package com.widyu.auth.application.guardian;

import com.widyu.auth.application.guardian.unlink.SocialUnlinkService;
import com.widyu.auth.dto.request.MemberWithdrawRequest;
import com.widyu.auth.event.MemberWithdrawnEvent;
import com.widyu.auth.event.SeniorWithdrawnEvent;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.fcm.application.degraded.EmergencyRecipientSnapshotService;
import com.widyu.goal.medicineschedule.application.MedicationProofDeletionService;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.deletion.S3ObjectDeletionTaskService;
import com.widyu.global.util.MemberUtil;
import com.widyu.global.util.SecurityUtil;
import com.widyu.location.access.repository.LocationAccessLogRepository;
import com.widyu.location.raw.repository.LocationFixRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final FamilyRepository familyRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberUtil memberUtil;
    private final SecurityUtil securityUtil;
    private final MedicationProofDeletionService medicationProofDeletionService;
    private final S3ObjectDeletionTaskService s3ObjectDeletionTaskService;
    private final SocialUnlinkService socialUnlinkService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmergencyRecipientSnapshotService emergencyRecipientSnapshots;
    private final LocationAccessLogRepository locationAccessLogRepository;
    private final LocationFixRepository locationFixRepository;

    @Transactional
    public void withdrawMember(MemberWithdrawRequest request) {
        Member member = memberUtil.getCurrentMember();
        // /api/v1/auth/**는 인가 규칙 없이 열려 있다. 어르신이 부르면 가족 연결이 남은 채 계정만 가려지므로
        // 어떤 처리보다 먼저 막는다. 어르신은 탈퇴 요청 API를 쓴다(LLD-0137).
        if (member.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        log.info("회원 탈퇴 시작: memberId={}", member.getId());

        // 외부 side-effect 전 방장 위임 여부 사전 검증
        validateLeaderCanWithdraw(member.getId());

        // FamilyMembership 처리 (leader 정책 적용)
        handleFamilyMembershipWithdrawal(member.getId());

        eraseAccount(member);

        log.info("회원 탈퇴 완료: memberId={}", member.getId());
    }

    /**
     * 어르신 본인이 앱에서 탈퇴하면 바로 실행한다. 유예 기간이 없다(2026-11-23 기획 결정, LLD-0186).
     * 가족 보호자에게 알림을 접수하고 가족에서 빠진 뒤, 위치 열람 기록과 위치 원본을 지우고 보호자 탈퇴와 같은
     * 계정 정리를 한다. 커밋 뒤 열린 실시간 연결을 닫는다({@code MemberWebSocketSessions}).
     */
    @Transactional
    public void withdrawSenior() {
        withdrawSenior(securityUtil.getCurrentMemberId());
    }

    /**
     * 회원 ID로 어르신 탈퇴를 실행한다. 본인 탈퇴와 운영자 탈퇴(앱을 못 써 메일로 요청한 경우, 본인 확인은 기획 담당)가
     * 같은 처리를 쓴다. 이미 탈퇴했으면 아무것도 하지 않고 {@code false}를 돌려준다.
     */
    @Transactional
    public boolean withdrawSenior(Long memberId) {
        // 잠금을 트랜잭션의 첫 읽기로 둔다. 같은 어르신이 두 번 눌러도 두 번째는 탈퇴 시각을 보고 끝난다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (member.getWithdrawnAt() != null) {
            return false;
        }

        log.info("어르신 탈퇴 시작: memberId={}", memberId);

        leaveFamily(member);

        // 처리방침: 어르신이 탈퇴하면 위치정보와 위치 열람 기록은 바로 지운다(ADR-0043 결정 8, 2026-11-23 사용자 결정).
        // 실증 참가자도 예외 없이 지운다(2026-11-23 기획자 답변).
        int accessLogs = locationAccessLogRepository.deleteBySeniorMemberId(memberId);
        int fixes = locationFixRepository.deleteByMemberId(memberId);

        eraseAccount(member);

        log.info("어르신 탈퇴 완료: memberId={}, accessLogs={}, locationFixes={}", memberId, accessLogs, fixes);
        return true;
    }

    /** 보호자와 어르신 탈퇴가 함께 쓰는 계정 정리. 가족 처리는 호출자가 먼저 한다. */
    private void eraseAccount(Member member) {
        medicationProofDeletionService.deleteAllByMember(member);

        // 1. 리프레시 토큰 삭제
        refreshTokenRepository.deleteById(member.getId());

        // 2. 소셜 연동 해제 작업 저장 (마스킹 전 oauthId·토큰으로, 외부 호출은 커밋 뒤)
        socialUnlinkService.schedule(member);

        // 3. 개인정보 마스킹 (GDPR 준수)
        String profileImage = member.getProfileImage();
        member.maskPersonalInfo();

        // 4. 비활성화와 탈퇴 시각 기록 (관리자 정지와 구분한다, LLD-0137)
        member.markWithdrawn(LocalDateTime.now());

        // 5. 회원 데이터 저장
        memberRepository.save(member);

        // 6. 프로필 사진 삭제 작업 저장 (커밋 뒤 삭제, 실패 시 재시도, 롤백되면 작업도 사라진다)
        s3ObjectDeletionTaskService.schedule(member.getId(), Stream.ofNullable(profileImage).toList());

        // 7. 커밋 뒤 소셜 연동 해제·Redis 위치 삭제
        eventPublisher.publishEvent(new MemberWithdrawnEvent(member.getId()));
    }

    /**
     * 어르신을 가족에서 뺀다. 방장 보호자 탈퇴로 가족이 해체될 때와 같은 「가족 없음」 상태다.
     * 가족 보호자는 가족 확인({@code FamilyAccessService})에서 걸려 더는 이 어르신을 보지 못한다.
     * 연결을 끊고 이름을 가리기 전에 보호자 목록과 이름을 잡아 탈퇴 알림(D01)을 접수한다.
     */
    private void leaveFamily(Member senior) {
        Long memberId = senior.getId();
        Optional<SeniorProfile> profileOpt = seniorProfileRepository.findByMemberId(memberId);
        if (profileOpt.isEmpty() || profileOpt.get().getFamily() == null) {
            return;
        }
        SeniorProfile profile = profileOpt.get();
        Long familyId = profile.getFamily().getId();
        List<Long> guardianIds = familyMembershipRepository.findAllByFamilyIdWithGuardian(familyId).stream()
                .map(membership -> membership.getGuardian().getId())
                .toList();
        eventPublisher.publishEvent(SeniorWithdrawnEvent.of(memberId, senior.getName(), guardianIds));
        profile.leaveFamily();
        // DB 장애 중 저하 경로가 탈퇴한 어르신의 수신자 스냅샷을 쓰지 않게 커밋 뒤 지운다(LLD-0087 5.2).
        emergencyRecipientSnapshots.evictAfterCommit(familyId, List.of(memberId));
    }

    private void validateLeaderCanWithdraw(Long guardianId) {
        Optional<FamilyMembership> membershipOpt = familyMembershipRepository.findByGuardianId(guardianId);
        if (membershipOpt.isEmpty() || !membershipOpt.get().isLeader()) {
            return;
        }
        long memberCount = familyMembershipRepository.countByFamilyId(membershipOpt.get().getFamily().getId());
        if (memberCount > 1) {
            throw new BusinessException(ErrorCode.FAMILY_LEADER_MUST_DELEGATE_BEFORE_WITHDRAW);
        }
    }

    private void handleFamilyMembershipWithdrawal(Long guardianId) {
        Optional<FamilyMembership> membershipOpt = familyMembershipRepository.findByGuardianId(guardianId);
        if (membershipOpt.isEmpty()) {
            return;
        }
        FamilyMembership membership = membershipOpt.get();
        // 탈퇴한 보호자가 DB 장애 중 저하 경로 알림을 계속 받지 않게 커밋 뒤 스냅샷을 정리한다(LLD-0087 5.2).
        if (membership.isLeader()) {
            Long familyId = membership.getFamily().getId();
            // 가족이 해체되면 가족 ID로 시니어를 다시 찾을 수 없으므로 해체 전에 잡아 둔다.
            List<Long> seniorMemberIds = seniorProfileRepository.findSeniorMemberIds(familyId);
            seniorProfileRepository.clearFamilyByFamilyId(familyId);
            familyMembershipRepository.deleteByGuardianId(guardianId);
            familyRepository.deleteById(familyId);
            emergencyRecipientSnapshots.evictAfterCommit(familyId, seniorMemberIds);
            return;
        }
        Long familyId = membership.getFamily().getId();
        familyMembershipRepository.deleteByGuardianId(guardianId);
        familyRepository.incrementOrderRevision(familyId);
        emergencyRecipientSnapshots.refreshFamilyAfterCommit(familyId);
    }
}
