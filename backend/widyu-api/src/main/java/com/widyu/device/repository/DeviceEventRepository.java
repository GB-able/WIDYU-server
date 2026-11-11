package com.widyu.device.repository;

import com.widyu.device.DeviceEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long> {

    boolean existsByDeviceIdAndEventId(String deviceId, String eventId);

    /** 시간대 내보내기: 한 회원의 구간 안 상태 바뀜을 바뀐 시각 순으로(LLD-0129 5절). 양 끝 포함. */
    List<DeviceEvent> findByMemberIdAndOccurredAtMsBetweenOrderByOccurredAtMsAsc(
            Long memberId, Long startMs, Long endMs);
}
