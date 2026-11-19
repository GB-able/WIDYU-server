package com.widyu.fcm.event.goal.listener;

import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmOutboxService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.GuardianDeepLinks;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.fcm.event.goal.dto.GoalAchievedEvent;
import com.widyu.fcm.event.goal.dto.GoalsAchievedEvent;
import com.widyu.member.FamilyMembership;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoalPointNotificationListener {

    private final FcmOutboxService outboxService;
    private final SeniorProfileRepository seniorProfileRepository;
    private final FamilyMembershipRepository familyMembershipRepository;

    @EventListener
    public void onGoalAchieved(GoalAchievedEvent event) {
        SeniorProfile profile = seniorProfile(event.seniorId());
        Map<String, String> seniorValues = Map.of("목표명", event.goalName(),
                "포인트", Long.toString(event.points()));
        NotificationCopy seniorCopy = NotificationCopy.of(NotificationType.GOAL_ACHIEVED, "G01-S", seniorValues);
        outboxService.enqueue(event.seniorId(), FcmSendDto.builder()
                .title(seniorCopy.title()).content(seniorCopy.body())
                .notificationType(NotificationType.GOAL_ACHIEVED).eventId(event.eventId())
                .entityId(event.entityId()).seniorId(event.seniorId()).build());

        Map<String, String> guardianValues = Map.of("시니어 이름", profile.getMember().getName(),
                "목표명", event.goalName(), "포인트", Long.toString(event.points()));
        NotificationCopy guardianCopy = NotificationCopy.of(NotificationType.GOAL_ACHIEVED, "G01-C", guardianValues);
        for (FamilyMembership membership : guardians(profile)) {
            outboxService.enqueue(membership.getGuardian().getId(), FcmSendDto.builder()
                    .title(guardianCopy.title()).content(guardianCopy.body())
                    .notificationType(NotificationType.GOAL_ACHIEVED).eventId(event.eventId())
                    .entityId(event.entityId()).seniorId(event.seniorId())
                    .relatedMemberId(event.seniorId()).deepLink(event.guardianDeepLink()).build());
        }
    }

    @EventListener
    public void onGoalsAchieved(GoalsAchievedEvent event) {
        SeniorProfile profile = seniorProfile(event.seniorId());
        NotificationType type = NotificationType.GOALS_ACHIEVED_GROUPED;
        String count = Integer.toString(event.count());
        String totalPoints = Long.toString(event.totalPoints());
        NotificationCopy seniorCopy = NotificationCopy.of(type, "G02-S",
                Map.of("달성 개수", count, "포인트 합계", totalPoints));
        outboxService.enqueue(event.seniorId(), FcmSendDto.builder()
                .title(seniorCopy.title()).content(seniorCopy.body())
                .notificationType(type).eventId(event.eventId()).seniorId(event.seniorId()).build());

        NotificationCopy guardianCopy = NotificationCopy.of(type, "G02-C", Map.of(
                "시니어 이름", profile.getMember().getName(), "달성 개수", count, "포인트 합계", totalPoints));
        for (FamilyMembership membership : guardians(profile)) {
            outboxService.enqueue(membership.getGuardian().getId(), FcmSendDto.builder()
                    .title(guardianCopy.title()).content(guardianCopy.body())
                    .notificationType(type).eventId(event.eventId()).seniorId(event.seniorId())
                    .relatedMemberId(event.seniorId()).deepLink(GuardianDeepLinks.goal(event.seniorId())).build());
        }
    }

    private SeniorProfile seniorProfile(Long seniorId) {
        return seniorProfileRepository.findByMemberId(seniorId)
                .orElseThrow(() -> new IllegalStateException("목표 달성 시니어 프로필이 없습니다."));
    }

    private List<FamilyMembership> guardians(SeniorProfile profile) {
        if (profile.getFamily() == null) {
            return List.of();
        }
        return familyMembershipRepository.findAllByFamilyIdWithGuardian(profile.getFamily().getId());
    }
}
