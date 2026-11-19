package com.widyu.fcm.application;

import com.widyu.fcm.DeliveryMode;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.FcmOutbox;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.PushSettingGroup;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FcmEligibility {
    private final MemberRepository members;
    private final FamilyMembershipRepository memberships;
    private final SeniorProfileRepository seniors;
    private final NotificationSettingService settings;

    public Long familyId(Long memberId) {
        return seniors.findFamilyIdByMemberId(memberId)
                .orElseGet(() -> memberships.findFamilyIdByGuardianId(memberId).orElse(null));
    }

    public boolean active(Long memberId) {
        return members.findById(memberId).filter(member -> member.getStatus() == Status.ACTIVE).isPresent();
    }

    public boolean sameActiveFamily(Long recipientId, Long relatedMemberId, Long familyId) {
        return active(relatedMemberId) && familyId != null
                && familyId.equals(familyId(recipientId))
                && familyId.equals(familyId(relatedMemberId));
    }

    public boolean eligible(FcmOutbox outbox) {
        Long recipientId = outbox.getRecipientMember().getId();
        Member recipient = members.findById(recipientId).orElse(null);
        if (recipient == null || recipient.getStatus() != Status.ACTIVE
                || !outbox.getMemberFcmToken().isActive()
                || !recipientId.equals(outbox.getMemberFcmToken().getMember().getId())) {
            return false;
        }
        if (!settingAllows(recipientId, recipient.getType(), outbox.getNotificationType(), outbox.getFcmCategory())) {
            return false;
        }
        Long related = outbox.getRelatedMemberId();
        if (related == null || related.equals(recipientId)) {
            return true;
        }
        return sameActiveFamily(recipientId, related, outbox.getFamilyId());
    }

    /**
     * 수신자가 이 알림의 설정 그룹을 켜 두었는지. 설정을 보지 않는 알림은 true다.
     * 발송(claim·preflight)과 접수(센터 행 생성, ADR-0046)가 같은 규칙을 쓴다.
     */
    public boolean settingAllows(Long recipientId, MemberType recipientType, NotificationType type,
                                 FcmCategory category) {
        PushSettingGroup group = settingGroup(type, category, recipientType);
        if (group == null || group == PushSettingGroup.NONE) {
            return true;
        }
        return settings.isNotificationEnabled(recipientId, group);
    }

    /** null이면 개인 설정을 보지 않는다. */
    private PushSettingGroup settingGroup(NotificationType type, FcmCategory category, MemberType recipientType) {
        // 시니어가 받는 푸시는 끌 수 없다(위듀 알림 정책 2절, LLD-0095). 남아 있는 시니어 OFF 행은 보지 않는다.
        if (recipientType == MemberType.SENIOR) {
            return null;
        }
        if (type == null) {
            return PushSettingGroup.fromLegacy(category);
        }
        if (type.deliveryMode() == DeliveryMode.DATA_ONLY) {
            return null;
        }
        return type.settingGroup();
    }
}
