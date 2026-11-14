package com.widyu.location.realtime.dto;

/** 응답에 실린 좌표가 어디서 왔는지(LLD-0141). 좌표의 신선도는 측정 시각으로 판단한다. */
public enum LocationSource {
    /** Redis 최신 위치. 실시간에 반영된 마지막 위치다. 오래됐을 수 있다. */
    LATEST,
    /** 체류 상태의 좌표. 체류가 시작된 지점이며 마지막으로 잰 시각은 모른다. */
    STAY,
    /** 이 요청에 실린 좌표. 실시간에 반영하지 않은 위치의 ACK에만 쓴다. */
    REQUEST
}
