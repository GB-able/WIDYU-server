package com.widyu.location.realtime.application;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 같은 회원의 실시간 위치 처리(최신 판단 → 체류, 안전구역 판정 → 최신 위치 저장 → 방송 요청)를 한 번에 하나씩 실행한다
 * (LLD-0139). 최신 시각 비교와 최신 위치 저장 사이에 다른 위치가 끼어들면 먼저 잰 위치가 최신 위치를 덮기 때문이다.
 *
 * <p>호출자는 DB 트랜잭션을 열기 전에 부른다. 그래서 기다리는 동안 커넥션을 쥐지 않는다. 회원마다 잠금이 따로라
 * 다른 회원은 기다리지 않는다. 한 회원에 몰린 위치가 수신 스레드를 다 붙잡지 않도록 대기 수와 대기 시간에 상한을 둔다.
 *
 * <p>ponytail: API 단일 인스턴스(architecture/websocket.md 7절) 전제의 JVM 안 잠금이다. 인스턴스를 늘리면
 * 회원별 처리 위치를 한 곳으로 고정하는 설계가 따로 필요하다.
 */
@Component
public class MemberLocationSequencer {

    private final int maxWaiting;
    private final long waitTimeoutMs;
    private final ConcurrentHashMap<Long, Gate> gates = new ConcurrentHashMap<>();

    public MemberLocationSequencer(
            @Value("${location.realtime.sequencer.max-waiting:4}") int maxWaiting,
            @Value("${location.realtime.sequencer.wait-timeout-ms:3000}") long waitTimeoutMs
    ) {
        this.maxWaiting = maxWaiting;
        this.waitTimeoutMs = waitTimeoutMs;
    }

    /**
     * 이 회원의 앞선 처리가 끝난 뒤 {@code work}를 실행한다. 이미 {@code maxWaiting}건이 기다리고 있거나
     * {@code waitTimeoutMs} 안에 차례가 오지 않으면 실행하지 않고 {@link LocationSequenceBusyException}을 던진다.
     */
    public <T> T run(Long memberId, Supplier<T> work) {
        Gate gate = enter(memberId);
        try {
            acquire(gate);
            try {
                return work.get();
            } finally {
                gate.lock.unlock();
            }
        } finally {
            leave(memberId);
        }
    }

    /** 처리 중 1건과 대기 {@code maxWaiting}건까지만 받는다. compute는 키마다 원자적이라 인원 수를 따로 잠그지 않는다. */
    private Gate enter(Long memberId) {
        return gates.compute(memberId, (id, existing) -> {
            Gate gate = existing;
            if (gate == null) {
                gate = new Gate();
            }
            if (gate.users > maxWaiting) {
                throw new LocationSequenceBusyException();
            }
            gate.users++;
            return gate;
        });
    }

    private void acquire(Gate gate) {
        try {
            if (!gate.lock.tryLock(waitTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new LocationSequenceBusyException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocationSequenceBusyException();
        }
    }

    /** 마지막 사용자가 나가면 항목을 지워 회원 수만큼 잠금이 쌓이지 않게 한다. */
    private void leave(Long memberId) {
        gates.computeIfPresent(memberId, (id, gate) -> {
            gate.users--;
            if (gate.users == 0) {
                return null;
            }
            return gate;
        });
    }

    int trackedMembers() {
        return gates.size();
    }

    /** 공정 잠금이라 먼저 기다린 위치가 먼저 처리된다. 처리 순서는 그래도 측정 시각 비교가 정한다. */
    private static final class Gate {
        private final ReentrantLock lock = new ReentrantLock(true);
        private int users;
    }
}
