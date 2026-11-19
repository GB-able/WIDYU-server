package com.widyu.location.access.application;

import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.repository.LocationAccessLogRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시니어 한 명의 위치 열람 요약 선점과 FCM 등록을 하나의 트랜잭션으로 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationAccessDigestSender {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M'월' d'일'");

    private final LocationAccessLogRepository locationAccessLogRepository;
    private final FcmService fcmService;

    /**
     * 미통보 행을 먼저 조건부 선점하고 같은 트랜잭션에서 FCM outbox에 등록한다.
     * 등록이 실패하면 선점도 롤백되므로 다음 스케줄 실행에서 다시 시도할 수 있다.
     */
    @Transactional
    public void send(Long seniorMemberId, LocalDateTime now) {
        int claimed = locationAccessLogRepository.claimUnnotified(seniorMemberId, now);
        if (claimed == 0) {
            log.info("위치 열람 요약 통보 건너뜀 - 다른 인스턴스가 선점함 seniorMemberId={}", seniorMemberId);
            return;
        }

        List<LocationAccessLog> logs =
                locationAccessLogRepository.findBySeniorMemberIdAndNotifiedAt(seniorMemberId, now);
        long viewerCount = logs.stream()
                .map(LocationAccessLog::getViewerMemberId)
                .distinct()
                .count();

        LocalDateTime firstAccessedAt = logs.stream()
                .map(LocationAccessLog::getAccessedAt)
                .min(LocalDateTime::compareTo)
                .orElse(now);
        NotificationType type = NotificationType.LOCATION_ACCESS_DIGEST;
        NotificationCopy copy = NotificationCopy.of(type, "L02", Map.of(
                "시작 날짜", firstAccessedAt.format(DATE_FORMATTER),
                "보호자 수", String.valueOf(viewerCount),
                "횟수", String.valueOf(logs.size())));
        fcmService.sendMessageToUser(seniorMemberId, FcmSendDto.of(type, copy, null, null, null, null, null));

        log.info("위치 열람 요약 통보 seniorMemberId={} viewerCount={} accessCount={}",
                seniorMemberId, viewerCount, logs.size());
    }
}
