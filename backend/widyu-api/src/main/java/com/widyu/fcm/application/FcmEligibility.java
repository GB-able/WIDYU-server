package com.widyu.fcm.application;

import com.widyu.fcm.FcmOutbox;
import com.widyu.fcm.DeliveryMode;
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
        PushSettingGroup group = settingGroup(outbox, recipient.getType());
        if (group != null && group != PushSettingGroup.NONE) {
            if (!settings.isNotificationEnabled(recipientId, group)) {
                return false;
            }
        }
        Long related = outbox.getRelatedMemberId();
        if (related == null || related.equals(recipientId)) {
            return true;
        }
        return sameActiveFamily(recipientId, related, outbox.getFamilyId());
    }

    /** null이면 개인 설정을 보지 않는다. */
    private PushSettingGroup settingGroup(FcmOutbox outbox, MemberType recipientType) {
        // 시니어가 받는 푸시는 끌 수 없다(위듀 알림 정책 2절, LLD-0095). 남아 있는 시니어 OFF 행은 보지 않는다.
        if (recipientType == MemberType.SENIOR) {
            return null;
        }
        if (outbox.getNotificationType() == null) {
            return PushSettingGroup.fromLegacy(outbox.getFcmCategory());
        }
        if (outbox.getNotificationType().deliveryMode() == DeliveryMode.DATA_ONLY) {
            return null;
        }
        return outbox.getNotificationType().settingGroup();
    }
}
