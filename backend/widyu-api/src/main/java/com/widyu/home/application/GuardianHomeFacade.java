package com.widyu.home.application;

import com.widyu.home.application.GuardianHomeService.GuardianHomeCards;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 보호자 홈 카드를 읽고, 그 읽기 트랜잭션이 끝난 뒤 위치 열람 기록을 남긴다(LLD-0152).
 *
 * <p>트랜잭션을 두지 않는다. 읽기 트랜잭션 안에서 기록(REQUIRES_NEW)을 부르면 요청 하나가 커넥션 두 개를 쥐어,
 * 동시 요청이 풀 크기를 넘으면 기록이 커넥션 대기로 실패하고 WARN만 남긴 채 빠진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardianHomeFacade {

    private final GuardianHomeService guardianHomeService;
    private final LocationAccessLogService locationAccessLogService;

    public GuardianHomeCardsResponse getHomeCards(Long memberId) {
        GuardianHomeCards cards = guardianHomeService.getHomeCards(memberId);

        // 외출 여부는 좌표가 아니지만 위치에서 파생되므로 열람으로 센다(ADR-0036 결과).
        recordAccessQuietly(cards.guardianId(), cards.seniorId());
        return cards.response();
    }

    /** 기록이 깨져도 홈 카드는 그려져야 한다(LLD-0056 5.1). */
    private void recordAccessQuietly(Long guardianId, Long seniorMemberId) {
        try {
            locationAccessLogService.record(guardianId, seniorMemberId, LocationAccessPath.HOME_OUTING);
        } catch (Exception e) {
            log.warn("위치 열람 기록 실패: path={}, cause={}",
                    LocationAccessPath.HOME_OUTING, e.getClass().getSimpleName());
        }
    }
}
