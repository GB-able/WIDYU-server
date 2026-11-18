package com.widyu.sensor.application;

import com.widyu.sensor.ClockMapping;
import com.widyu.sensor.repository.ClockMappingRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 첫 등록 INSERT와 재조회를 호출 트랜잭션과 분리해, 동시에 먼저 커밋된 행을 새 스냅샷에서 읽게 한다. */
@Service
@RequiredArgsConstructor
public class ClockMappingInsertService {

    private final ClockMappingRepository clockMappingRepository;

    /** 없을 때만 넣는다. 동시에 같은 매핑을 넣는 요청이 있어도 중복 키 예외 없이 끝나며, 행은 하나만 남는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertIfAbsent(ClockMapping mapping) {
        clockMappingRepository.insertIfAbsent(mapping, LocalDateTime.now());
    }

    /**
     * 첫 등록 뒤 남은 행(내 것이든 먼저 커밋된 다른 요청의 것이든)을 새 스냅샷에서 읽는다.
     * 호출 트랜잭션은 MySQL REPEATABLE READ에서 첫 조회 때 고정한 스냅샷을 유지하므로, 같은 트랜잭션에서 다시 읽으면 다른 요청이 커밋한 행이 보이지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ClockMapping> findCommitted(String clockMappingId) {
        return clockMappingRepository.findByClockMappingId(clockMappingId);
    }
}
