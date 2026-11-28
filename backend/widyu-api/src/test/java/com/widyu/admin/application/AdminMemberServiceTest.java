package com.widyu.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.widyu.admin.AdminAction;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.auth.application.guardian.MemberWithdrawService;
import com.widyu.fcm.repository.MemberFcmTokenRepository;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.pay.repository.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminMemberService 예외 처리 단위 테스트")
class AdminMemberServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private MemberFcmTokenRepository memberFcmTokenRepository;
    @Mock private AlbumRepository albumRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private HeartRateEmergencyRepository heartRateEmergencyRepository;
    @Mock private AdminAuditLogService adminAuditLogService;
    @Mock private MemberWithdrawService memberWithdrawService;

    @InjectMocks
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("존재하지 않는 회원 상세 조회 시 MEMBER_NOT_FOUND 예외를 던진다")
    void 존재하지_않는_회원_상세_조회_시_예외가_발생한다() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminMemberService.getMemberDetail(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("허용되지 않는 회원 상태 변경 시 FORBIDDEN 예외를 던진다")
    void 허용되지_않는_회원_상태_변경_시_예외가_발생한다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01011112222");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> adminMemberService.changeStatus(1L, Status.DELETED))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("ACTIVE 또는 INACTIVE만 허용됩니다.");
    }

    @Test
    @DisplayName("운영자가 어르신 탈퇴를 실행하면 본인 탈퇴와 같은 처리를 하고 감사 로그를 남긴다")
    void 운영자가_어르신_탈퇴를_실행하면_감사_로그를_남긴다() {
        // given
        given(memberWithdrawService.withdrawSenior(7L)).willReturn(true);

        // when
        boolean withdrawn = adminMemberService.withdrawSenior(7L);

        // then
        assertThat(withdrawn).isTrue();
        verify(adminAuditLogService).logInCurrentTransaction(
                AdminAction.SENIOR_WITHDRAWN_BY_ADMIN, "MEMBER", 7L, "memberId=7");
    }

    @Test
    @DisplayName("이미 탈퇴한 어르신을 운영자가 다시 탈퇴시키면 감사 로그를 남기지 않는다")
    void 이미_탈퇴한_어르신을_운영자가_다시_탈퇴시키면_감사_로그를_남기지_않는다() {
        // given
        given(memberWithdrawService.withdrawSenior(7L)).willReturn(false);

        // when
        boolean withdrawn = adminMemberService.withdrawSenior(7L);

        // then
        assertThat(withdrawn).isFalse();
        verifyNoInteractions(adminAuditLogService);
    }
}
