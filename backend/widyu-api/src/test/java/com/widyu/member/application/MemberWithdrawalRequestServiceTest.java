package com.widyu.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.widyu.admin.AdminAction;
import com.widyu.admin.application.AdminAuditLogService;
import com.widyu.admin.application.AdminIdentityResolver;
import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.SecurityUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.MemberWithdrawalRequest;
import com.widyu.member.MemberWithdrawalRequestStatus;
import com.widyu.member.dto.response.MemberWithdrawalRequestResponse;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.MemberWithdrawalRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("어르신 탈퇴 요청 서비스")
class MemberWithdrawalRequestServiceTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Mock private SecurityUtil securityUtil;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberWithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private AdminAuditLogService adminAuditLogService;
    @Mock private AdminIdentityResolver adminIdentityResolver;

    @InjectMocks
    private MemberWithdrawalRequestService service;

    private static Member member(MemberType type, Long id) {
        Member member = Member.createMember(type, "회원", "01000000000");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static MemberWithdrawalRequest openRequest(Long requestId, Long memberId) {
        MemberWithdrawalRequest request = MemberWithdrawalRequest.request(memberId, REQUESTED_AT);
        ReflectionTestUtils.setField(request, "id", requestId);
        return request;
    }

    @Test
    @DisplayName("어르신이 탈퇴를 요청하면 처리 대기 요청이 저장되고 처리 기한은 10일 뒤다")
    void 어르신이_탈퇴를_요청하면_처리_대기_요청이_저장된다() {
        // given
        given(securityUtil.getCurrentMemberId()).willReturn(7L);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member(MemberType.SENIOR, 7L)));
        given(withdrawalRequestRepository.findFirstByMemberIdAndStatus(7L, MemberWithdrawalRequestStatus.REQUESTED))
                .willReturn(Optional.empty());
        given(withdrawalRequestRepository.save(any(MemberWithdrawalRequest.class))).willAnswer(invocation -> {
            MemberWithdrawalRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        // when
        MemberWithdrawalRequestResponse response = service.request();

        // then
        assertThat(response.requestId()).isEqualTo(12L);
        assertThat(response.memberId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(MemberWithdrawalRequestStatus.REQUESTED);
        assertThat(response.dueAt()).isEqualTo(response.requestedAt().plusDays(10));
        assertThat(response.processedAt()).isNull();
    }

    @Test
    @DisplayName("처리 대기 요청이 있는 어르신이 다시 요청하면 회원 행을 잠근 뒤 기존 요청을 돌려준다")
    void 열린_요청이_있으면_새로_만들지_않고_기존_요청을_돌려준다() {
        // given
        given(securityUtil.getCurrentMemberId()).willReturn(7L);
        given(memberRepository.findByIdForUpdate(7L)).willReturn(Optional.of(member(MemberType.SENIOR, 7L)));
        given(withdrawalRequestRepository.findFirstByMemberIdAndStatus(7L, MemberWithdrawalRequestStatus.REQUESTED))
                .willReturn(Optional.of(openRequest(12L, 7L)));

        // when
        MemberWithdrawalRequestResponse response = service.request();

        // then
        assertThat(response.requestId()).isEqualTo(12L);
        assertThat(response.requestedAt()).isEqualTo(REQUESTED_AT);
        verify(withdrawalRequestRepository, never()).save(any());
        InOrder order = inOrder(memberRepository, withdrawalRequestRepository);
        order.verify(memberRepository).findByIdForUpdate(7L);
        order.verify(withdrawalRequestRepository)
                .findFirstByMemberIdAndStatus(7L, MemberWithdrawalRequestStatus.REQUESTED);
    }

    @Test
    @DisplayName("보호자가 탈퇴 요청을 부르면 접근 거부 예외가 발생하고 아무것도 저장하지 않는다")
    void 보호자가_탈퇴_요청을_부르면_접근_거부된다() {
        // given
        given(securityUtil.getCurrentMemberId()).willReturn(3L);
        given(memberRepository.findByIdForUpdate(3L)).willReturn(Optional.of(member(MemberType.GUARDIAN, 3L)));

        // when & then
        assertThatThrownBy(() -> service.request())
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(withdrawalRequestRepository);
    }

    @Test
    @DisplayName("관리자가 열린 요청을 처리 완료하면 처리 기록과 식별자만 담은 감사 로그가 남는다")
    void 관리자가_열린_요청을_처리_완료하면_처리_기록과_감사_로그가_남는다() {
        // given
        MemberWithdrawalRequest request = openRequest(12L, 7L);
        given(withdrawalRequestRepository.findByIdForUpdate(12L)).willReturn(Optional.of(request));
        given(adminIdentityResolver.resolveAdminId()).willReturn(1L);

        // when
        MemberWithdrawalRequestResponse response = service.complete(12L);

        // then
        assertThat(response.status()).isEqualTo(MemberWithdrawalRequestStatus.PROCESSED);
        assertThat(response.processedAt()).isNotNull();
        assertThat(response.processedByAdminId()).isEqualTo(1L);
        verify(adminAuditLogService).logInCurrentTransaction(
                AdminAction.MEMBER_WITHDRAWAL_REQUEST_PROCESSED, "MEMBER_WITHDRAWAL_REQUEST", 12L, "memberId=7");
    }

    @Test
    @DisplayName("관리자가 열린 요청을 취소하면 취소 기록과 감사 로그가 남는다")
    void 관리자가_열린_요청을_취소하면_취소_기록과_감사_로그가_남는다() {
        // given
        MemberWithdrawalRequest request = openRequest(12L, 7L);
        given(withdrawalRequestRepository.findByIdForUpdate(12L)).willReturn(Optional.of(request));
        given(adminIdentityResolver.resolveAdminId()).willReturn(1L);

        // when
        MemberWithdrawalRequestResponse response = service.cancel(12L);

        // then
        assertThat(response.status()).isEqualTo(MemberWithdrawalRequestStatus.CANCELLED);
        assertThat(response.processedByAdminId()).isEqualTo(1L);
        verify(adminAuditLogService).logInCurrentTransaction(
                AdminAction.MEMBER_WITHDRAWAL_REQUEST_CANCELLED, "MEMBER_WITHDRAWAL_REQUEST", 12L, "memberId=7");
    }

    @Test
    @DisplayName("이미 처리 완료된 요청을 다시 처리하면 충돌 예외가 발생하고 감사 로그를 남기지 않는다")
    void 이미_처리된_요청을_다시_처리하면_충돌_예외가_발생한다() {
        // given
        MemberWithdrawalRequest request = openRequest(12L, 7L);
        request.complete(1L, REQUESTED_AT.plusDays(1));
        given(withdrawalRequestRepository.findByIdForUpdate(12L)).willReturn(Optional.of(request));
        given(adminIdentityResolver.resolveAdminId()).willReturn(2L);

        // when & then
        assertThatThrownBy(() -> service.cancel(12L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_WITHDRAWAL_REQUEST_CLOSED);
        assertThat(request.getStatus()).isEqualTo(MemberWithdrawalRequestStatus.PROCESSED);
        verify(adminAuditLogService, never()).logInCurrentTransaction(any(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("없는 요청을 처리하면 찾을 수 없음 예외가 발생한다")
    void 없는_요청을_처리하면_찾을_수_없음_예외가_발생한다() {
        // given
        given(withdrawalRequestRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.complete(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_WITHDRAWAL_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("관리자가 상태로 거르면 그 상태의 요청을 오래된 순서로 돌려준다")
    void 관리자가_상태로_거르면_그_상태의_요청을_돌려준다() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 20);
        given(withdrawalRequestRepository.findByStatusOrderByIdAsc(MemberWithdrawalRequestStatus.REQUESTED, pageRequest))
                .willReturn(new PageImpl<>(List.of(openRequest(10L, 5L), openRequest(12L, 7L)), pageRequest, 2));

        // when
        AdminPageResponse<MemberWithdrawalRequestResponse> page =
                service.getRequests(MemberWithdrawalRequestStatus.REQUESTED, 0, 20);

        // then
        assertThat(page.content()).extracting(MemberWithdrawalRequestResponse::requestId).containsExactly(10L, 12L);
        assertThat(page.totalElements()).isEqualTo(2);
    }
}
