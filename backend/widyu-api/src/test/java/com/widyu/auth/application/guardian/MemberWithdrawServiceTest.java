package com.widyu.auth.application.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.widyu.auth.application.guardian.unlink.SocialUnlinkService;
import com.widyu.auth.dto.request.MemberWithdrawRequest;
import com.widyu.auth.event.MemberWithdrawnEvent;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.fcm.application.degraded.EmergencyRecipientSnapshotService;
import com.widyu.goal.medicineschedule.application.MedicationProofDeletionService;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.deletion.S3ObjectDeletionTaskService;
import com.widyu.global.util.MemberUtil;
import com.widyu.global.util.SecurityUtil;
import com.widyu.auth.event.SeniorWithdrawnEvent;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.member.SeniorProfile;
import com.widyu.location.access.repository.LocationAccessLogRepository;
import com.widyu.location.raw.repository.LocationFixRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberWithdrawService 단위 테스트")
class MemberWithdrawServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private MemberUtil memberUtil;
    @Mock private SecurityUtil securityUtil;
    @Mock private MedicationProofDeletionService medicationProofDeletionService;
    @Mock private SocialUnlinkService socialUnlinkService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private S3ObjectDeletionTaskService s3ObjectDeletionTaskService;
    @Mock private EmergencyRecipientSnapshotService emergencyRecipientSnapshots;
    @Mock private LocationAccessLogRepository locationAccessLogRepository;
    @Mock private LocationFixRepository locationFixRepository;

    @InjectMocks
    private MemberWithdrawService memberWithdrawService;

    @Test
    @DisplayName("소셜 계정이 없는 회원 탈퇴 시 리프레시 토큰 삭제 및 회원 정보를 저장한다")
    void 소셜계정_없는_회원_탈퇴_시_토큰_삭제하고_회원_저장() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("서비스 불만족"));

        // then
        verify(refreshTokenRepository).deleteById(1L);
        verify(medicationProofDeletionService).deleteAllByMember(member);
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("FamilyMembership이 없는 guardian 탈퇴 시 예외 없이 완료된다")
    void FamilyMembership_없는_guardian_탈퇴_시_예외없이_완료된다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when & then
        assertThatCode(() -> memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유")))
                .doesNotThrowAnyException();
        verify(familyMembershipRepository, never()).deleteByGuardianId(any());
    }

    @Test
    @DisplayName("방장이 아닌 구성원 탈퇴 시 FamilyMembership만 삭제된다")
    void 방장_아닌_구성원_탈퇴_시_멤버십만_삭제된다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());

        Family family = Family.createFamily("ABCDEF");
        ReflectionTestUtils.setField(family, "id", 100L);
        FamilyMembership membership = FamilyMembership.createMembership(family, member);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.of(membership));

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(familyMembershipRepository).deleteByGuardianId(1L);
        verify(familyRepository).incrementOrderRevision(100L);
        verify(familyRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("방장이 아닌 구성원이 탈퇴하면 커밋 뒤 그 가족의 긴급 수신자 스냅샷을 다시 만들도록 예약한다")
    void 방장_아닌_구성원이_탈퇴하면_커밋뒤_스냅샷_갱신을_예약한다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        Family family = Family.createFamily("ABCDEF");
        ReflectionTestUtils.setField(family, "id", 100L);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(familyMembershipRepository.findByGuardianId(1L))
                .willReturn(Optional.of(FamilyMembership.createMembership(family, member)));

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(emergencyRecipientSnapshots).refreshFamilyAfterCommit(100L);
        verify(emergencyRecipientSnapshots, never()).evictAfterCommit(any(), any());
    }

    @Test
    @DisplayName("마지막 구성원인 방장이 탈퇴하면 가족 해체 전에 잡은 시니어들의 스냅샷을 커밋 뒤 지우도록 예약한다")
    void 마지막_방장이_탈퇴하면_해체전_시니어들의_스냅샷_삭제를_예약한다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        Family family = Family.createFamily("ABCDEF");
        ReflectionTestUtils.setField(family, "id", 100L);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(familyMembershipRepository.findByGuardianId(1L))
                .willReturn(Optional.of(FamilyMembership.createLeaderMembership(family, member)));
        given(familyMembershipRepository.countByFamilyId(100L)).willReturn(1L);
        given(seniorProfileRepository.findSeniorMemberIds(100L)).willReturn(List.of(7L, 8L));

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        InOrder order = inOrder(seniorProfileRepository);
        order.verify(seniorProfileRepository).findSeniorMemberIds(100L);
        order.verify(seniorProfileRepository).clearFamilyByFamilyId(100L);
        verify(emergencyRecipientSnapshots).evictAfterCommit(100L, List.of(7L, 8L));
        verify(emergencyRecipientSnapshots, never()).refreshFamilyAfterCommit(any());
    }

    @Test
    @DisplayName("방장이며 다른 구성원이 있을 때 탈퇴 시 예외가 발생한다")
    void 방장이며_다른_구성원_있을_때_탈퇴_시_예외가_발생한다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());

        Family family = Family.createFamily("ABCDEF");
        ReflectionTestUtils.setField(family, "id", 100L);
        FamilyMembership leaderMembership = FamilyMembership.createLeaderMembership(family, member);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.of(leaderMembership));
        given(familyMembershipRepository.countByFamilyId(100L)).willReturn(2L);

        // when & then
        assertThatThrownBy(() -> memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유")))
                .isInstanceOf(BusinessException.class);
        verify(familyRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("소셜 계정 보유 방장이 다른 구성원 있을 때 탈퇴 차단 시 소셜 revoke를 호출하지 않는다")
    void 소셜_계정_보유_방장이_다른_구성원_있을_때_탈퇴_차단_시_소셜_revoke_호출하지_않는다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        SocialAccount kakaoAccount = SocialAccount.createSocialAccount("k@k.com", "kakao", "kakao-id", member);
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(kakaoAccount));

        Family family = Family.createFamily("ABCDEF");
        ReflectionTestUtils.setField(family, "id", 100L);
        FamilyMembership leaderMembership = FamilyMembership.createLeaderMembership(family, member);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.of(leaderMembership));
        given(familyMembershipRepository.countByFamilyId(100L)).willReturn(2L);

        // when & then
        assertThatThrownBy(() -> memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유")))
                .isInstanceOf(BusinessException.class);
        verify(socialUnlinkService, never()).schedule(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("방장이며 마지막 구성원일 때 탈퇴 시 Family가 삭제된다")
    void 방장이며_마지막_구성원일_때_탈퇴_시_Family가_삭제된다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());

        Family family = Family.createFamily("ABCDEF");
        ReflectionTestUtils.setField(family, "id", 100L);
        FamilyMembership leaderMembership = FamilyMembership.createLeaderMembership(family, member);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.of(leaderMembership));
        given(familyMembershipRepository.countByFamilyId(100L)).willReturn(1L);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(seniorProfileRepository).clearFamilyByFamilyId(100L);
        verify(familyMembershipRepository).deleteByGuardianId(1L);
        verify(familyRepository).deleteById(100L);
    }

    @Test
    @DisplayName("소셜 계정이 있는 회원이 탈퇴하면 마스킹 전 값으로 연동 해제를 예약하고 탈퇴 이벤트를 발행한다")
    void 소셜_계정_보유_회원_탈퇴_시_마스킹_전_값으로_연동_해제를_예약하고_이벤트를_발행한다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        SocialAccount appleAccount = SocialAccount.createSocialAccount(
                "a@a.com", "APPLE", "apple-id", "apple-refresh-token", member
        );
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(appleAccount));
        given(memberUtil.getCurrentMember()).willReturn(member);
        List<String> scheduledWith = new ArrayList<>();
        willAnswer(invocation -> {
            scheduledWith.add(appleAccount.getOauthId());
            scheduledWith.add(appleAccount.getRefreshToken());
            return null;
        }).given(socialUnlinkService).schedule(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        assertThat(scheduledWith).containsExactly("apple-id", "apple-refresh-token");
        verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(1L));
    }

    @Test
    @DisplayName("회원 탈퇴 시 개인정보가 마스킹된다")
    void 회원_탈퇴_시_개인정보가_마스킹된다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(memberRepository).save(
                org.mockito.ArgumentMatchers.argThat(
                        m -> !"홍길동".equals(m.getName())
                )
        );
    }

    @Test
    @DisplayName("회원 탈퇴 시 연동 해제에 쓴 소셜 리프레시 토큰과 프로필 사진을 비운다")
    void 회원_탈퇴_시_소셜_토큰과_프로필_사진을_비운다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        member.updateProfileImage("https://bucket/profile/a.jpg");
        SocialAccount appleAccount = SocialAccount.createSocialAccount(
                "a@a.com", "APPLE", "apple-id", "apple-refresh-token", member
        );
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(appleAccount));
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        assertThat(appleAccount.getRefreshToken()).isNull();
        assertThat(member.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("회원 탈퇴 시 기존 프로필 사진의 삭제 작업을 저장한다")
    void 회원_탈퇴_시_프로필_사진_삭제_작업을_저장한다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        member.updateProfileImage("https://bucket/profile/a.jpg");
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(s3ObjectDeletionTaskService).schedule(1L, List.of("https://bucket/profile/a.jpg"));
    }

    @Test
    @DisplayName("어르신이 보호자 탈퇴를 부르면 접근 거부 예외가 발생하고 계정과 가족 연결이 그대로다")
    void 어르신이_보호자_탈퇴를_부르면_접근_거부되고_아무것도_바뀌지_않는다() {
        // given
        Member senior = Member.createMember(MemberType.SENIOR, "김어르신", "01099998888");
        ReflectionTestUtils.setField(senior, "id", 9L);
        ReflectionTestUtils.setField(senior, "socialAccounts", new ArrayList<>());
        given(memberUtil.getCurrentMember()).willReturn(senior);

        // when & then
        assertThatThrownBy(() -> memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(senior.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(senior.getName()).isEqualTo("김어르신");
        assertThat(senior.getPhoneNumber()).isEqualTo("01099998888");
        assertThat(senior.getWithdrawnAt()).isNull();
        verifyNoInteractions(refreshTokenRepository, medicationProofDeletionService, socialUnlinkService,
                familyMembershipRepository, seniorProfileRepository, memberRepository,
                s3ObjectDeletionTaskService, eventPublisher);
    }

    @Test
    @DisplayName("보호자가 탈퇴하면 비활성이 되고 탈퇴 시각이 남는다")
    void 보호자가_탈퇴하면_비활성이_되고_탈퇴_시각이_남는다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        assertThat(member.getStatus()).isEqualTo(Status.INACTIVE);
        assertThat(member.getWithdrawnAt()).isNotNull();
    }

    private static Member senior(Long id) {
        Member member = Member.createMember(MemberType.SENIOR, "김어르신", "01099998888");
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        return member;
    }

    private static SeniorProfile seniorProfileInFamily(Member senior, Long familyId) {
        Family family = Family.createFamily("ABCDEF");
        ReflectionTestUtils.setField(family, "id", familyId);
        return SeniorProfile.createSeniorProfile(senior, family, "서울시", "ABC1234", LocalDate.of(1950, 1, 1));
    }

    @Test
    @DisplayName("어르신이 탈퇴하면 가족 보호자 알림을 접수하고 가족에서 빠지고 위치 기록을 지우고 계정을 가린다")
    void 어르신이_탈퇴하면_보호자_알림을_접수하고_가족에서_빠지고_위치_기록을_지우고_계정을_가린다() {
        // given
        Member senior = senior(7L);
        SeniorProfile profile = seniorProfileInFamily(senior, 100L);
        Member guardianA = Member.createMember(MemberType.GUARDIAN, "딸", "01011112222");
        ReflectionTestUtils.setField(guardianA, "id", 21L);
        Member guardianB = Member.createMember(MemberType.GUARDIAN, "아들", "01011113333");
        ReflectionTestUtils.setField(guardianB, "id", 22L);
        given(securityUtil.getCurrentMemberId()).willReturn(7L);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(senior));
        given(seniorProfileRepository.findByMemberId(7L)).willReturn(Optional.of(profile));
        given(familyMembershipRepository.findAllByFamilyIdWithGuardian(100L)).willReturn(List.of(
                FamilyMembership.createMembership(profile.getFamily(), guardianA),
                FamilyMembership.createMembership(profile.getFamily(), guardianB)));

        // when
        memberWithdrawService.withdrawSenior();

        // then
        assertThat(profile.getFamily()).isNull();
        assertThat(senior.getStatus()).isEqualTo(Status.INACTIVE);
        assertThat(senior.getWithdrawnAt()).isNotNull();
        assertThat(senior.getName()).isEqualTo("탈퇴회원");
        assertThat(senior.getPhoneNumber()).isNull();
        verify(locationAccessLogRepository).deleteBySeniorMemberId(7L);
        verify(locationFixRepository).deleteByMemberId(7L);
        verify(refreshTokenRepository).deleteById(7L);
        verify(medicationProofDeletionService).deleteAllByMember(senior);
        verify(emergencyRecipientSnapshots).evictAfterCommit(100L, List.of(7L));
        verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(7L));
        // 이름을 가리기 전의 이름으로 알림을 접수한다.
        verify(eventPublisher).publishEvent(SeniorWithdrawnEvent.of(7L, "김어르신", List.of(21L, 22L)));
    }

    @Test
    @DisplayName("보호자를 어르신 탈퇴로 처리하면 권한 예외가 발생하고 아무것도 지우지 않는다")
    void 보호자를_어르신_탈퇴로_처리하면_권한_예외가_발생한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(guardian, "id", 1L);
        given(securityUtil.getCurrentMemberId()).willReturn(1L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(guardian));

        // when & then
        assertThatThrownBy(() -> memberWithdrawService.withdrawSenior())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(locationAccessLogRepository, locationFixRepository, refreshTokenRepository);
        assertThat(guardian.getWithdrawnAt()).isNull();
    }

    @Test
    @DisplayName("같은 어르신이 탈퇴를 두 번 눌러 두 번째 요청이 잠금 뒤에 오면 아무것도 바꾸지 않는다")
    void 탈퇴를_두_번_누르면_두_번째는_아무것도_바꾸지_않는다() {
        // given
        Member senior = senior(7L);
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 11, 1, 9, 0);
        senior.markWithdrawn(withdrawnAt);
        given(securityUtil.getCurrentMemberId()).willReturn(7L);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(senior));

        // when
        memberWithdrawService.withdrawSenior();

        // then
        assertThat(senior.getWithdrawnAt()).isEqualTo(withdrawnAt);
        verifyNoInteractions(locationAccessLogRepository, locationFixRepository, eventPublisher);
    }

    @Test
    @DisplayName("운영자가 정지한 어르신도 회원 ID로 탈퇴를 실행하면 탈퇴 처리되고 true를 돌려준다")
    void 정지된_어르신도_회원_ID로_탈퇴하면_탈퇴_처리된다() {
        // given
        Member senior = senior(7L);
        senior.withdraw();
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(senior));
        given(seniorProfileRepository.findByMemberId(7L)).willReturn(Optional.empty());

        // when
        boolean withdrawn = memberWithdrawService.withdrawSenior(7L);

        // then
        assertThat(withdrawn).isTrue();
        assertThat(senior.getWithdrawnAt()).isNotNull();
        assertThat(senior.getName()).isEqualTo("탈퇴회원");
        verify(locationFixRepository).deleteByMemberId(7L);
        verifyNoInteractions(securityUtil);
    }

    @Test
    @DisplayName("이미 탈퇴한 어르신을 회원 ID로 다시 탈퇴시키면 false를 돌려주고 아무것도 바꾸지 않는다")
    void 이미_탈퇴한_어르신을_회원_ID로_다시_탈퇴시키면_false를_돌려준다() {
        // given
        Member senior = senior(7L);
        senior.markWithdrawn(LocalDateTime.of(2026, 11, 1, 9, 0));
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(senior));

        // when
        boolean withdrawn = memberWithdrawService.withdrawSenior(7L);

        // then
        assertThat(withdrawn).isFalse();
        verifyNoInteractions(locationFixRepository, eventPublisher);
    }
}
