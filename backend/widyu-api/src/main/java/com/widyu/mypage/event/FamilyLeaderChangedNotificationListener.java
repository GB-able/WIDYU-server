package com.widyu.mypage.event;

import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmOutboxService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FamilyLeaderChangedNotificationListener {

    /** 위듀(어르신) 앱의 가족 관리 화면. 보호자는 타입 템플릿 `/family-manage`를 쓴다. */
    private static final String SENIOR_FAMILY_MANAGE = "/my/management";

    private final FcmOutboxService outboxService;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;

    @EventListener
    public void onLeaderChanged(FamilyLeaderChangedEvent event) {
        NotificationType type = NotificationType.FAMILY_LEADER_CHANGED;
        NotificationCopy copy = NotificationCopy.of(type, "R01", Map.of());
        outboxService.enqueue(event.newLeaderId(), message(copy, type.deepLinkTemplate(), event.newLeaderId()));

        // R02: 새 방장과 방장을 넘긴 본인을 뺀 같은 가족의 보호자와 어르신(위듀·위듀케어 정책서 「다른 사람이 방장이 됨」).
        List<FamilyMembership> memberships = familyMembershipRepository.findAllByFamilyIdWithGuardian(event.familyId());
        String newLeaderName = memberships.stream()
                .map(FamilyMembership::getGuardian)
                .filter(guardian -> guardian.getId().equals(event.newLeaderId()))
                .map(Member::getName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
        NotificationCopy others = NotificationCopy.of(type, "R02", Map.of("이름", newLeaderName));
        for (FamilyMembership membership : memberships) {
            Long guardianId = membership.getGuardian().getId();
            if (guardianId.equals(event.newLeaderId()) || guardianId.equals(event.previousLeaderId())) {
                continue;
            }
            outboxService.enqueue(guardianId, message(others, type.deepLinkTemplate(), event.newLeaderId()));
        }
        for (Long seniorId : seniorProfileRepository.findSeniorMemberIds(event.familyId())) {
            outboxService.enqueue(seniorId, message(others, SENIOR_FAMILY_MANAGE, event.newLeaderId()));
        }
    }

    private FcmSendDto message(NotificationCopy copy, String deepLink, Long newLeaderId) {
        NotificationType type = NotificationType.FAMILY_LEADER_CHANGED;
        return FcmSendDto.builder()
                .title(copy.title())
                .content(copy.body())
                .fcmCategory(type.fcmCategory())
                .notificationType(type)
                .scheme(deepLink)
                .deepLink(deepLink)
                .relatedMemberId(newLeaderId)
                .build();
    }
}
