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
 * <p>같은 회원의 더 늦게 잰 위치가 이미 들어와 있으면 먼저 잰 위치는 기다리지 않고 {@code superseded}로 끝낸다(LLD-0140).
 * 그 위치는 실시간에 반영돼도 곧 더 최근 위치로 바뀌므로 할 일이 없다. 반대로 지금까지 들어온 것보다 늦게 잰 위치는
 * 대기 상한에 걸리지 않는다. 상한 때문에 가장 최근 위치가 실시간에 빠지던 문제를 막고, 먼저 잰 대기자는 짧은 간격으로
 * 이를 확인해 빠지므로 붙잡힌 스레드 수는 상한 근처에 머문다.
 *
 * <p>ponytail: API 단일 인스턴스(architecture/websocket.md 7절) 전제의 JVM 안 잠금이다. 인스턴스를 늘리면
 * 회원별 처리 위치를 한 곳으로 고정하는 설계가 따로 필요하다.
 */
@Component
public class MemberLocationSequencer {

    /** 기다리는 위치가 더 최근 위치에 밀렸는지 다시 보는 간격. */
    private static final long SUPERSEDE_CHECK_MS = 10;

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
     * 이 회원의 앞선 처리가 끝난 뒤 {@code work}를 실행한다. 더 늦게 잰 위치가 이미 들어와 있으면 {@code superseded}를
     * 돌려준다. 잰 시각이 없는 옛 형식({@code measuredAtMs == null})은 밀리지도 밀어내지도 않는다.
     * 더 최근 위치가 아닌데 이미 {@code maxWaiting}건이 기다리거나 {@code waitTimeoutMs} 안에 차례가 오지 않으면
     * {@link LocationSequenceBusyException}을 던진다.
     */
    public <T> T run(Long memberId, Long measuredAtMs, Supplier<T> work, Supplier<T> superseded) {
        boolean[] supersededOnEntry = {false};
        Gate gate = enter(memberId, measuredAtMs, supersededOnEntry);
        if (supersededOnEntry[0]) {
            return superseded.get();
        }
        try {
            if (!acquire(gate, measuredAtMs)) {
                return superseded.get();
            }
            try {
                return work.get();
            } finally {
                gate.lock.unlock();
            }
        } finally {
            leave(memberId);
        }
    }

    /** compute는 키마다 원자적이라 인원 수와 최근 시각을 따로 잠그지 않는다. 밀린 위치는 인원에 넣지 않는다. */
    private Gate enter(Long memberId, Long measuredAtMs, boolean[] supersededOnEntry) {
        return gates.compute(memberId, (id, existing) -> {
            Gate gate = existing;
            if (gate == null) {
                gate = new Gate();
            }
            if (isOlderThanNewest(gate, measuredAtMs)) {
                supersededOnEntry[0] = true;
                return gate;
            }
            if (gate.users > maxWaiting && !isNewerThanNewest(gate, measuredAtMs)) {
                throw new LocationSequenceBusyException();
            }
            gate.users++;
            if (isNewerThanNewest(gate, measuredAtMs)) {
                gate.newestMeasuredAtMs = measuredAtMs;
            }
            return gate;
        });
    }

    /** 차례를 얻으면 true, 기다리는 동안이나 차례를 얻은 직후 더 최근 위치에 밀렸으면 false. */
    private boolean acquire(Gate gate, Long measuredAtMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitTimeoutMs);
        try {
            while (true) {
                if (isOlderThanNewest(gate, measuredAtMs)) {
                    return false;
                }
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMs <= 0) {
                    throw new LocationSequenceBusyException();
                }
                if (gate.lock.tryLock(Math.min(SUPERSEDE_CHECK_MS, remainingMs), TimeUnit.MILLISECONDS)) {
                    if (isOlderThanNewest(gate, measuredAtMs)) {
                        gate.lock.unlock();
                        return false;
                    }
                    return true;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocationSequenceBusyException();
        }
    }

    /** 마지막 사용자가 나가면 항목을 지워 회원 수만큼 잠금이 쌓이지 않게 한다. 최근 시각도 함께 사라진다. */
    private void leave(Long memberId) {
        gates.computeIfPresent(memberId, (id, gate) -> {
            gate.users--;
            if (gate.users == 0) {
                return null;
            }
            return gate;
        });
    }

    private static boolean isOlderThanNewest(Gate gate, Long measuredAtMs) {
        Long newest = gate.newestMeasuredAtMs;
        return measuredAtMs != null && newest != null && measuredAtMs < newest;
    }

    private static boolean isNewerThanNewest(Gate gate, Long measuredAtMs) {
        Long newest = gate.newestMeasuredAtMs;
        return measuredAtMs != null && (newest == null || measuredAtMs > newest);
    }

    int trackedMembers() {
        return gates.size();
    }

    /** 공정 잠금이라 먼저 기다린 위치가 먼저 처리된다. 처리 순서는 그래도 측정 시각 비교가 정한다. */
    private static final class Gate {
        private final ReentrantLock lock = new ReentrantLock(true);
        private int users;
        // 들어온(밀리지 않은) 위치 중 가장 늦게 잰 시각. 기다리는 스레드가 잠금 밖에서 읽는다.
        private volatile Long newestMeasuredAtMs;
    }
}
