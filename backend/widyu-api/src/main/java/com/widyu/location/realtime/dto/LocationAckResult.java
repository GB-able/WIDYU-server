package com.widyu.location.realtime.dto;

/**
 * 위치 ACK의 처리 결과(LLD-0123). 셋 다 앱이 큐에서 지워도 된다는 뜻이다. 원본 저장에 실패하면 ACK를 보내지 않는다.
 */
public enum LocationAckResult {
    /** 원본을 새로 저장했다. */
    STORED,
    /** 같은 (device_id, session_id, seq)가 이미 저장돼 있다. */
    DUPLICATE,
    /** 요청 자체가 잘못돼 다시 보내도 같은 결과다. 저장·방송하지 않았다. */
    REJECTED
}
