package com.widyu.fcm.event.goal.walk.listener;

import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.fcm.NotificationType;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.walk.Walk;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalkNotificationListener {

    private final FcmService fcmService;
    private final WalkRepository walkRepository;
    private final SeniorProfileRepository seniorProfileRepository;

    /**
     * 19시 미달성 판정 전에 앱을 깨워 오늘 걸음 수를 올리게 하는 데이터 전용 신호(W03).
     */
    @Scheduled(cron = "0 55 18 * * *")
    @Transactional
    public void sendWalkDataSync() {
        LocalDate today = LocalDate.now();
        List<Member> members = seniorProfileRepository.findMembersWithWalkGoal(Status.ACTIVE);
        log.info("걸음 수 동기화 신호 발송 - 대상 날짜: {}, 대상: {}명", today, members.size());

        NotificationType type = NotificationType.WALK_DATA_SYNC;
        for (Member member : members) {
            try {
                fcmService.sendMessageToUser(member.getId(), FcmSendDto.builder()
                        .fcmCategory(type.fcmCategory()).notificationType(type)
                        .eventId(type.name() + ":" + member.getId() + ":" + today)
                        .build());
            } catch (Exception e) {
                log.error("걸음 수 동기화 신호 발송 실패 - memberId: {}", member.getId(), e);
            }
        }
    }

    /**
     * 매일 저녁 7시에 실행되어 오늘 만보계 목표 미달성자에게 알림 발송
     */
    @Scheduled(cron = "0 0 19 * * *")
    @Transactional
    public void sendWalkGoalReminderToUnachieved() {
        LocalDate today = LocalDate.now();

        log.info("만보계 목표 미달성 알림 스케줄러 실행 - 대상 날짜: {}", today);

        // 오늘 걸음 기록이 있는 미달성자만 본다. 기록이 없거나 0걸음인 날은 보내지 않는다(#1120)
        List<Walk> unachievedWalks = walkRepository.findUnachievedWalksByDate(today);
        if (unachievedWalks.isEmpty()) {
            log.info("오늘 만보계 목표 미달성자가 없습니다.");
            return;
        }

        log.info("{}명의 시니어에게 만보계 목표 미달성 알림을 발송합니다.", unachievedWalks.size());

        for (Walk walk : unachievedWalks) {
            if (walk.isGoalAchieved()) {
                continue;
            }
            sendQuietly(walk.getMember(), walk.getActualSteps(), today);
        }
    }

    private void sendQuietly(Member member, Integer actualSteps, LocalDate today) {
        try {
            sendUnachievedNotification(member, actualSteps, today);
        } catch (Exception e) {
            log.error("만보계 미달성 알림 발송 실패 - memberId: {}", member.getId(), e);
        }
    }

    /**
     * 개별 미달성자에게 알림 발송. 0걸음은 걷지 않았는지 워치를 안 찼는지 동기화가 안 됐는지 서버가 구분할 수 없어
     * 보내지 않는다(알림 정책서 위듀 v12 13절 추천, 사용자 결정 2026-11-18, #1120. #801의 0걸음 발송을 되돌림).
     */
    private void sendUnachievedNotification(Member member, Integer actualSteps, LocalDate today) {
        if (member.getType() != MemberType.SENIOR || actualSteps == null || actualSteps <= 0) {
            return;
        }
        NotificationCopy copy = NotificationCopy.of(NotificationType.WALK_GOAL_UNMET, "W01",
                Map.of("실제 걸음 수", actualSteps.toString()));
        fcmService.sendMessageToUser(member.getId(), FcmSendDto.builder()
                .title(copy.title()).content(copy.body())
                .fcmCategory(NotificationType.WALK_GOAL_UNMET.fcmCategory())
                .notificationType(NotificationType.WALK_GOAL_UNMET)
                // 같은 날 다시 실행돼도 알림센터 행이 하나만 남게 회원과 날짜로 정한다.
                .eventId("W01:" + member.getId() + ":" + today)
                .image(member.getProfileImage()).build());
    }
}
