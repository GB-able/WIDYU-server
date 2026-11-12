package com.widyu.member.application;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 어르신 탈퇴 요청과 운영자의 처리 기록. 서버는 계정을 바꾸지 않는다 → LLD-0137 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawalRequestService {

    private static final String AUDIT_TARGET_TYPE = "MEMBER_WITHDRAWAL_REQUEST";

    private final SecurityUtil securityUtil;
    private final MemberRepository memberRepository;
    private final MemberWithdrawalRequestRepository withdrawalRequestRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminIdentityResolver adminIdentityResolver;

    @Transactional
    public MemberWithdrawalRequestResponse request() {
        // 같은 어르신의 동시 요청을 회원 행 잠금으로 줄 세워 열린 요청을 하나로 유지한다.
        // 잠금을 트랜잭션의 첫 읽기로 둔다. 앞서 일반 읽기를 하면 MySQL REPEATABLE READ 스냅샷이 잠금 전에 잡혀
        // 먼저 커밋된 다른 요청을 보지 못한다.
        Member current = memberRepository.findByIdForUpdate(securityUtil.getCurrentMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (current.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        MemberWithdrawalRequest request = withdrawalRequestRepository
                .findFirstByMemberIdAndStatus(current.getId(), MemberWithdrawalRequestStatus.REQUESTED)
                .orElseGet(() -> create(current.getId()));
        return MemberWithdrawalRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<MemberWithdrawalRequestResponse> getRequests(
            MemberWithdrawalRequestStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<MemberWithdrawalRequest> result;
        if (status == null) {
            result = withdrawalRequestRepository.findAllByOrderByIdAsc(pageRequest);
        } else {
            result = withdrawalRequestRepository.findByStatusOrderByIdAsc(status, pageRequest);
        }
        return AdminPageResponse.from(result.map(MemberWithdrawalRequestResponse::from));
    }

    @Transactional
    public MemberWithdrawalRequestResponse complete(Long requestId) {
        MemberWithdrawalRequest request = findForUpdate(requestId);
        request.complete(adminIdentityResolver.resolveAdminId(), LocalDateTime.now());
        audit(AdminAction.MEMBER_WITHDRAWAL_REQUEST_PROCESSED, request);
        return MemberWithdrawalRequestResponse.from(request);
    }

    @Transactional
    public MemberWithdrawalRequestResponse cancel(Long requestId) {
        MemberWithdrawalRequest request = findForUpdate(requestId);
        request.cancel(adminIdentityResolver.resolveAdminId(), LocalDateTime.now());
        audit(AdminAction.MEMBER_WITHDRAWAL_REQUEST_CANCELLED, request);
        return MemberWithdrawalRequestResponse.from(request);
    }

    private MemberWithdrawalRequest create(Long memberId) {
        MemberWithdrawalRequest saved = withdrawalRequestRepository.save(
                MemberWithdrawalRequest.request(memberId, LocalDateTime.now()));
        log.info("어르신 탈퇴 요청 접수: memberId={}, requestId={}", memberId, saved.getId());
        return saved;
    }

    private MemberWithdrawalRequest findForUpdate(Long requestId) {
        return withdrawalRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_WITHDRAWAL_REQUEST_NOT_FOUND));
    }

    // 처리와 같은 트랜잭션에 남겨, 감사 로그가 실패하면 처리도 남지 않게 한다. detail에는 식별자만 담는다.
    private void audit(AdminAction action, MemberWithdrawalRequest request) {
        adminAuditLogService.logInCurrentTransaction(
                action, AUDIT_TARGET_TYPE, request.getId(), "memberId=" + request.getMemberId());
    }
}
