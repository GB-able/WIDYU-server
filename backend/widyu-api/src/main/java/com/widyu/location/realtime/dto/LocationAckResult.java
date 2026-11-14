package com.widyu.location.realtime.dto;

/**
 * v2 위치 ACK의 처리 결과(LLD-0143). 셋 다 서버가 이 위치의 처리를 끝냈다는 뜻이고 앱이 다시 보낼 필요가 없다.
 * 보호자가 받았다는 뜻은 아니다. 위치 원본은 저장하지 않는다. 기술 오류나 대기 초과면 ACK를 보내지 않는다.
 */
public enum LocationAckResult {
    /** 판정을 커밋하고 Redis 최신 위치에 반영했다. */
    APPLIED,
    /** 정상 요청이지만 반영할 필요가 없다(15분 지남, 최신보다 먼저 잰 위치, 같은 위치 재전송, 더 늦은 위치가 반영됨). */
    SKIPPED,
    /** 요청 자체가 잘못돼 다시 보내도 같은 결과다. 판정, 방송하지 않았다. */
    REJECTED
}
