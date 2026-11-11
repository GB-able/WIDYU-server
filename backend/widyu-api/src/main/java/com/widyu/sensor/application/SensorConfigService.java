package com.widyu.sensor.application;

import com.widyu.global.properties.SensorProperties;
import com.widyu.run.application.CollectionRunService;
import com.widyu.sensor.dto.response.SensorConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워치에 내려보내는 수집 설정(LLD-0046 5절, 지시서 B12).
 * 수집 모드는 참가자별 값이 아니라 <b>열린 측정회차</b>로 정한다. 열린 회차가 있으면 그 회차의
 * {@code collection_mode}를 그대로 내려 배치 설정 대조의 기준과 맞춘다. 회차 열기·닫기(#643)가
 * 곧 전환 수단이라 관리자 변경 API를 따로 두지 않는다(합의 대기 K5 회신). 회차가 없으면 서버 설정의
 * 기본 모드를 쓴다. 개발 서버는 이 값을 research로 두어 회차 없이도 실험 모드로 수집한다(#989).
 */
@Service
@RequiredArgsConstructor
public class SensorConfigService {

    private final SensorProperties sensorProperties;
    private final CollectionRunService collectionRunService;

    @Transactional(readOnly = true)
    public SensorConfigResponse currentConfig(Long memberId) {
        return SensorConfigResponse.of(sensorProperties.config(), collectionModeOf(memberId));
    }

    private String collectionModeOf(Long memberId) {
        return collectionRunService.findOpenRunCollectionMode(memberId)
                .orElseGet(() -> sensorProperties.config().defaultCollectionMode());
    }
}
