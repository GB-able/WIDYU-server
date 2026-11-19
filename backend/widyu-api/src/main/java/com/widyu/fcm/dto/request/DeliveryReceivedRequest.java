package com.widyu.fcm.dto.request;

/** 기기 시계로 잰 도착 시각(epoch ms). 없어도 된다. 서버는 참고로만 저장한다(LLD-0181). */
public record DeliveryReceivedRequest(
        Long deviceReceivedAtMs
) {
}
