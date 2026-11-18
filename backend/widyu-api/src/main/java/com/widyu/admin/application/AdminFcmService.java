package com.widyu.admin.application;

import com.widyu.admin.AdminAction;
import com.widyu.admin.dto.request.AdminFcmTestRequest;
import com.widyu.admin.dto.response.AdminFcmTestResponse;
import com.widyu.admin.dto.response.AdminMemberDetailResponse;
import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.fcm.DeliveryMode;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.application.FcmTestAttempt;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.member.MemberRole;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFcmService {

    private static final String TEST_VALUE = "관리자";

    private final FcmService fcmService;
    private final MemberRepository memberRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional(readOnly = true)
    public List<Member> searchMembers(String name) {
        return memberRepository.findTop20ByNameContainingOrderByIdDesc(name);
    }

    @Transactional(readOnly = true)
    public List<Member> getAllMembers() {
        return memberRepository.findTop50ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminMemberDetailResponse> getMemberPage(String name, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id").descending());
        Page<AdminMemberDetailResponse> result;
        if (name != null && !name.isBlank()) {
            result = memberRepository.findByNameContainingOrderByIdDesc(name, pageRequest).map(AdminMemberDetailResponse::from);
        } else {
            result = memberRepository.findAllByOrderByIdDesc(pageRequest).map(AdminMemberDetailResponse::from);
        }
        return AdminPageResponse.from(result);
    }

    public AdminFcmTestResponse sendTestNotification(AdminFcmTestRequest request) {
        if (!memberRepository.existsById(request.memberId())) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        NotificationType type = request.notificationType();
        String title = blankToNull(request.title());
        String content = blankToNull(request.content());
        FcmCategory category = request.category();
        if (type != null) {
            category = type.fcmCategory();
            if (type.deliveryMode() != DeliveryMode.DATA_ONLY && title == null && content == null) {
                NotificationCopy copy = resolveCopy(type, request);
                title = copy.title();
                content = copy.body();
            }
        }
        if (category == null) {
            category = FcmCategory.ETC;
        }
        FcmSendDto fcmSendDto = FcmSendDto.builder()
                .title(title)
                .content(content)
                .fcmCategory(category)
                .notificationType(type)
                .scheme("")
                .image(null)
                .build();

        List<FcmTestAttempt> attempts = fcmService.sendTestMessageToUser(request.memberId(), fcmSendDto);
        AdminFcmTestResponse response = AdminFcmTestResponse.of(request.memberId(), type, attempts);
        adminAuditLogService.log(
                AdminAction.FCM_TEST_SEND, "MEMBER", request.memberId(),
                describe(type, title) + " → " + response.sent() + "/" + response.tokenCount() + "개 성공"
        );
        return response;
    }

    private NotificationCopy resolveCopy(NotificationType type, AdminFcmTestRequest request) {
        try {
            String code = request.copyCode();
            if (code == null || code.isBlank()) {
                code = NotificationCopy.defaultCode(type);
            }
            return NotificationCopy.of(type, code, fillMissingValues(code, request.values()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    // 테스트 발송이라 요청에 없는 문구 변수는 모두 "관리자"로 채운다.
    private Map<String, String> fillMissingValues(String code, Map<String, String> requested) {
        Map<String, String> values = new HashMap<>();
        if (requested != null) {
            values.putAll(requested);
        }
        for (String name : NotificationCopy.variables(code)) {
            values.putIfAbsent(name, TEST_VALUE);
        }
        return values;
    }

    // 관리자 화면이 빈 입력을 ""로 보내도 문구표 기본 문구를 쓰게 한다.
    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String describe(NotificationType type, String title) {
        if (type == null) {
            return "'" + title + "'";
        }
        return type.name() + " '" + title + "'";
    }
}
