package com.widyu.location.realtime.application;

/**
 * 같은 회원의 위치가 너무 많이 기다리고 있어 실시간 처리를 하지 않았다(LLD-0139). 요청이 잘못된 것이 아니라 일시적
 * 과부하라서 업무 예외({@code BusinessException})로 만들지 않는다. 업무 예외는 {@code REJECTED} ACK가 되어 앱이 지운다.
 */
public class LocationSequenceBusyException extends RuntimeException {

    public LocationSequenceBusyException() {
        super("같은 회원의 위치 처리 대기가 가득 찼습니다.");
    }
}
