package com.widyu.fcm.event.goal.healthschedule.listener;

import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.fcm.NotificationType;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthScheduleNotificationListener {

    private static final int WINDOW_MINUTES = 10;

    private final FcmService fcmService;
    private final HealthScheduleRepository healthScheduleRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final FamilyMembershipRepository familyMembershipRepository;

    /**
     * 10분마다 실행해, 실행 시각을 10분 단위로 내린 시각의 1시간 뒤부터 10분 동안 시작하는 일정에 알린다(LLD-0105).
     * 예: 14:20:00.04 실행 → 15:20:00 이상 15:30:00 미만. 회차 창이 겹치지도 비지도 않아 모든 일정이 한 번씩 든다.
     * 바깥 트랜잭션을 두지 않아 일정마다 따로 커밋하고, 한 건의 실패가 회차 전체를 롤백하지 않는다.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void sendHealthScheduleReminder() {
        sendHealthScheduleReminderAt(LocalDateTime.now());
    }

    public void sendHealthScheduleReminderAt(LocalDateTime runAt) {
        LocalDateTime minute = runAt.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime windowStart = minute.minusMinutes(minute.getMinute() % WINDOW_MINUTES).plusHours(1);
        LocalDateTime windowEnd = windowStart.plusMinutes(WINDOW_MINUTES);

        log.debug("건강 일정 알림 스케줄러 실행 - 대상 시간: {} ~ {}", windowStart, windowEnd);

        List<HealthSchedule> upcomingSchedules = healthScheduleRepository
                .findUpcomingSchedulesInTimeRange(windowStart, windowEnd);

        if (upcomingSchedules.isEmpty()) {
            log.debug("알림을 보낼 건강 일정이 없습니다.");
            return;
        }

        log.info("{}개의 건강 일정에 대해 알림을 발송합니다.", upcomingSchedules.size());

        for (HealthSchedule schedule : upcomingSchedules) {
            try {
                sendScheduleNotification(schedule);
            } catch (Exception e) {
                log.error("건강 일정 알림 발송 실패 - scheduleId: {}, memberId: {}",
                        schedule.getId(), schedule.getMember().getId(), e);
            }
        }
    }

    /**
     * 개별 일정에 대한 알림 발송
     */
    private void sendScheduleNotification(HealthSchedule schedule) {
        if (schedule.getScheduledAt() == null || schedule.getScheduleName() == null
                || schedule.getScheduleName().isBlank()) {
            log.warn("건강 일정 알림 필수 정보 누락: scheduleId={}", schedule.getId());
            return;
        }
        Member recipient = schedule.getMember();
        if (recipient.getType() == MemberType.GUARDIAN) {
            return;
        }
        String time = NotificationCopy.clockText(schedule.getScheduledAt());
        NotificationCopy osCopy = NotificationCopy.of(NotificationType.HEALTH_SCHEDULE_UPCOMING,
                "H01-S-OS", Map.of());
        NotificationCopy inAppCopy = NotificationCopy.of(NotificationType.HEALTH_SCHEDULE_UPCOMING,
                "H01-S-INAPP", Map.of(
                        "오전/오후 시각", time,
                        "일정명", schedule.getScheduleName()));
        FcmSendDto dto = FcmSendDto.of(NotificationType.HEALTH_SCHEDULE_UPCOMING, osCopy,
                schedule.getId().toString(), null, null, null, recipient.getProfileImage())
                .withCenterCopy(inAppCopy);
        fcmService.sendMessageToUser(recipient.getId(), dto);
        sendGuardianNotifications(schedule, recipient, time);
    }

    /**
     * H01-C: 어르신 일정을 같은 회차에 그 어르신 가족의 보호자 각각에게 알린다(LLD-0066 2026-11-18 갱신).
     * 수신 스위치는 타입의 설정 그룹(보호자 GENERAL)을 따른다. 가족 소속과 활성 여부는 접수 단계가 다시 확인한다.
     * eventId는 일정과 시각으로 정해 같은 회차를 다시 실행해도 보호자의 센터 행이 하나다.
     */
    private void sendGuardianNotifications(HealthSchedule schedule, Member senior, String time) {
        Long familyId = seniorProfileRepository.findFamilyIdByMemberId(senior.getId()).orElse(null);
        if (familyId == null) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        values.put("오전/오후 시각", time);
        values.put("일정명", schedule.getScheduleName());
        if (senior.getName() != null && !senior.getName().isBlank()) {
            values.put("시니어 이름", senior.getName());
        }
        NotificationType type = NotificationType.HEALTH_SCHEDULE_UPCOMING;
        NotificationCopy osCopy = NotificationCopy.of(type, "H01-C-SENIOR-OS", values);
        NotificationCopy inAppCopy = NotificationCopy.of(type, "H01-C-SENIOR-INAPP", values);
        String eventId = UUID.nameUUIDFromBytes(("H01C:" + schedule.getId() + ":" + schedule.getScheduledAt())
                .getBytes(StandardCharsets.UTF_8)).toString();
        FcmSendDto dto = FcmSendDto.builder()
                .title(osCopy.title()).content(osCopy.body())
                .centerTitle(inAppCopy.title()).centerBody(inAppCopy.body())
                .fcmCategory(type.fcmCategory()).notificationType(type).eventId(eventId)
                .entityId(schedule.getId().toString()).seniorId(senior.getId()).relatedMemberId(senior.getId())
                .image(senior.getProfileImage())
                .build();
        for (FamilyMembership membership : familyMembershipRepository.findAllByFamilyIdWithGuardian(familyId)) {
            Long guardianId = membership.getGuardian().getId();
            try {
                fcmService.sendMessageToUser(guardianId, dto);
            } catch (Exception e) {
                log.error("보호자 건강 일정 알림 발송 실패 - scheduleId: {}, guardianId: {}",
                        schedule.getId(), guardianId, e);
            }
        }
    }
}
