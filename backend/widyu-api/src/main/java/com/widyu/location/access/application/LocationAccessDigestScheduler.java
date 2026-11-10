package com.widyu.location.access.application;

import com.widyu.location.access.repository.LocationAccessLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아직 알리지 않은 열람 기록을 시니어별로 묶어 요약 통보한다(LLD-0056 5.4).
 *
 * <p>정기 요약(cron)은 기본으로 꺼져 있다. 하루 한 번 묶음은 동의가 있어도 허용되지 않아 열람마다
 * 바로 알리기로 했다(LLD-0130). 남은 일은 서버 시작 1분 뒤 한 번 도는 {@link #flushOnStartup()}이다.
 * 바로 알림 전환 전에 쌓인 미통보 기록, 그리고 쿨다운을 0보다 크게 두었을 때 건너뛴 기록이
 * 영영 알려지지 않는 일을 막는다.
 *
 * <p>시니어 한 명의 선점과 FCM 등록은 별도 빈의 트랜잭션으로 묶는다. 실패하면 그 시니어의
 * 선점만 롤백되어 다음 실행에서 재시도되고, 스케줄러의 try/catch가 다음 시니어 처리를 이어 간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationAccessDigestScheduler {

    private final LocationAccessLogRepository locationAccessLogRepository;
    private final LocationAccessDigestSender locationAccessDigestSender;

    /** Spring 6.1부터 {@code initialDelay}만 준 {@code @Scheduled}는 반복하지 않는 1회 작업이다. */
    @Scheduled(initialDelay = 60_000)
    public void flushOnStartup() {
        sendDigest();
    }

    @Scheduled(cron = "${location-access.digest-cron}")
    public void sendDigest() {
        List<Long> seniorIds = locationAccessLogRepository.findSeniorIdsWithUnnotified();
        if (seniorIds.isEmpty()) {
            log.info("위치 열람 요약 통보 대상 없음");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long seniorMemberId : seniorIds) {
            try {
                locationAccessDigestSender.send(seniorMemberId, now);
            } catch (Exception e) {
                log.warn("위치 열람 요약 통보 실패 seniorMemberId={} cause={}",
                        seniorMemberId, e.getClass().getSimpleName());
            }
        }
    }
}
