package com.widyu.sensor.application;

import com.widyu.sensor.SensorBatchRejection;
import com.widyu.sensor.repository.SensorBatchRejectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거절 기록을 자기 트랜잭션에 저장한다(LLD-0128 5.2). 예외를 삼키지 않는다.
 * 커밋 시점 예외는 프록시가 이 메서드 밖에서 던지므로 호출부가 감싸서 원래 오류를 지킨다.
 */
@Service
@RequiredArgsConstructor
public class SensorBatchRejectionRecorder {

    private final SensorBatchRejectionRepository rejections;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SensorBatchRejection rejection) {
        rejections.save(rejection);
    }
}
