package com.widyu.location.realtime.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원별 위치 작업 우편함(LLD-0144). 수신 스레드는 작업을 넣기만 하고 바로 돌아간다. 한 회원이 위치를 몰아 보내도 공용 수신
 * 스레드를 붙잡지 않고, 위치 실행기에서도 한 번에 한 작업만 차지한다.
 *
 * <p>회원마다 실행 중 1건, 최신 대기 1건, 밀린 항목 {@code maxWaitingPerMember}건까지만 둔다.
 * <ul>
 *   <li>최신 대기보다 늦게 잰 위치가 오면 최신 대기를 교체하고, 교체된 작업은 밀린 항목이 된다.</li>
 *   <li>같거나 먼저 잰 위치는 밀린 항목이 된다. 밀린 항목은 그보다 같거나 늦게 잰 작업이 반영되거나 생략으로 끝난 뒤에만 그
 *   결과로 답한다. 그 작업이 실패하면 기다리고, 회원에게 더 처리할 작업이 없으면 과부하로 끝낸다(ACK 없음).</li>
 *   <li>밀린 항목이 상한을 넘으면 가장 먼저 들어온 항목을 과부하로 끝낸다. 최신 위치는 늘 받는다.</li>
 * </ul>
 *
 * <p>실행기 대기열에는 회원마다 차례 하나만 들어간다. 작업 하나를 끝낸 회원은 더 할 일이 있으면 대기열 맨 뒤로 다시 들어가
 * 다른 회원과 번갈아 실행된다. 대기열 용량은 활성 회원 상한 이상이라 재등록이 거절되지 않는다(종료 중 제외).
 * 활성 회원 수와 보관 작업 수에도 전체 상한이 있고, 넘으면 새 작업을 과부하로 끝낸다. 수신 스레드에서 대신 실행하지 않는다.
 *
 * <p>ponytail: API 단일 인스턴스 전제(architecture/websocket.md 7절). 인스턴스를 늘리면 회원별 처리 위치를 한 곳으로 고정해야 한다.
 */
@Slf4j
public class MemberLocationMailbox {

    /** 작업 결과. 밀린 항목은 이 결과로 답한다. */
    public enum Outcome { APPLIED, SKIPPED, REJECTED, FAILED }

    /** 우편함에 넣는 작업. 콜백은 회원 상자의 잠금 밖, 실행기 스레드(과부하는 넣은 스레드)에서 부른다. */
    public interface Job {

        /** 비교 기준 시각(수신 때 한 번 정한 순서 시각). 잰 시각이 없는 옛 형식이면 null이고 도착 순서로 비교한다. */
        Long orderAtMs();

        /** 실행하고 자기 응답을 보낸 뒤 결과를 돌려준다. */
        Outcome run();

        /** 같거나 늦게 잰 작업이 {@code outcome}으로 끝나 이 작업도 끝났다. */
        void settledBy(Outcome outcome);

        /** 자리가 없거나 대신 처리할 작업이 없어 처리하지 못했다. 성공 응답을 보내지 않는다. */
        void overloaded();
    }

    private final Executor executor;
    private final int maxWaitingPerMember;
    private final int maxMembers;
    private final int maxQueued;
    private final ConcurrentHashMap<Long, Box> boxes = new ConcurrentHashMap<>();
    private final AtomicInteger activeMembers = new AtomicInteger();
    private final AtomicInteger queued = new AtomicInteger();

    public MemberLocationMailbox(Executor executor, int maxWaitingPerMember, int maxMembers, int maxQueued) {
        if (maxWaitingPerMember < 0 || maxMembers < 1 || maxQueued < 1) {
            throw new IllegalArgumentException("location mailbox limits must be positive (waiting may be 0)");
        }
        this.executor = executor;
        this.maxWaitingPerMember = maxWaitingPerMember;
        this.maxMembers = maxMembers;
        this.maxQueued = maxQueued;
    }

    /** 수신 스레드에서 부른다. 막히지 않는다. 자리가 없으면 {@link Job#overloaded()}를 이 스레드에서 부른다. */
    public void submit(Long memberId, Job job) {
        if (queued.incrementAndGet() > maxQueued) {
            queued.decrementAndGet();
            job.overloaded();
            return;
        }
        List<Runnable> after = new ArrayList<>();
        while (true) {
            Box box = boxes.computeIfAbsent(memberId, id -> newBox());
            if (box == null) {
                queued.decrementAndGet();
                job.overloaded();
                return;
            }
            synchronized (box) {
                if (box.closed) {
                    continue; // 방금 정리된 상자다. 새 상자를 받는다.
                }
                accept(memberId, box, new Entry(job, box.nextArrival++), after);
            }
            after.forEach(Runnable::run);
            return;
        }
    }

    /** 활성 회원 상한을 넘으면 만들지 않는다(computeIfAbsent가 null을 받으면 넣지 않는다). */
    private Box newBox() {
        if (activeMembers.incrementAndGet() > maxMembers) {
            activeMembers.decrementAndGet();
            return null;
        }
        return new Box();
    }

    private void accept(Long memberId, Box box, Entry entry, List<Runnable> after) {
        Entry newest = box.pending;
        if (newest == null) {
            newest = box.running;
        }
        if (!isNewer(entry, newest)) {
            addWaiting(box, entry, after);
            return;
        }
        if (box.pending != null) {
            addWaiting(box, box.pending, after);
        }
        box.pending = entry;
        if (box.running == null && !box.scheduled) {
            box.scheduled = true;
            after.add(() -> schedule(memberId, box));
        }
    }

    private void addWaiting(Box box, Entry entry, List<Runnable> after) {
        box.waiting.addLast(entry);
        while (box.waiting.size() > maxWaitingPerMember) {
            Entry evicted = box.waiting.pollFirst();
            queued.decrementAndGet();
            after.add(evicted.job::overloaded);
        }
    }

    private void schedule(Long memberId, Box box) {
        try {
            executor.execute(() -> drain(memberId, box));
        } catch (RejectedExecutionException e) {
            // 종료 중이다. 남은 작업을 과부하로 끝내고 상자를 정리한다.
            log.warn("위치 실행기가 작업을 거절했습니다(종료 중): memberId={}", memberId);
            List<Runnable> after = new ArrayList<>();
            synchronized (box) {
                box.scheduled = false;
                if (box.pending != null) {
                    Entry pending = box.pending;
                    box.pending = null;
                    queued.decrementAndGet();
                    after.add(pending.job::overloaded);
                }
                failWaiting(box, after);
                closeIfIdle(memberId, box);
            }
            after.forEach(Runnable::run);
        }
    }

    /** 실행기 스레드. 최신 대기 하나를 실행하고, 끝난 결과로 밀린 항목을 정리한 뒤 할 일이 남았으면 맨 뒤로 다시 들어간다. */
    private void drain(Long memberId, Box box) {
        Entry entry;
        synchronized (box) {
            box.scheduled = false;
            entry = box.pending;
            box.pending = null;
            box.running = entry;
        }
        if (entry == null) {
            return;
        }
        Outcome outcome = runSafely(entry);
        List<Runnable> after = new ArrayList<>();
        synchronized (box) {
            box.running = null;
            queued.decrementAndGet();
            if (outcome != Outcome.FAILED) {
                settleCovered(box, entry, outcome, after);
            }
            if (box.pending != null) {
                box.scheduled = true;
                after.add(() -> schedule(memberId, box));
            } else {
                // 대신 처리할 더 늦은 작업이 없다. 실패로 남은 밀린 항목은 과부하로 끝낸다(재전송을 받는다).
                failWaiting(box, after);
                closeIfIdle(memberId, box);
            }
        }
        after.forEach(Runnable::run);
    }

    private static Outcome runSafely(Entry entry) {
        try {
            return entry.job.run();
        } catch (RuntimeException e) {
            log.warn("위치 작업이 예외를 밖으로 던졌습니다: cause={}", e.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    private void settleCovered(Box box, Entry finished, Outcome outcome, List<Runnable> after) {
        Iterator<Entry> it = box.waiting.iterator();
        while (it.hasNext()) {
            Entry waiting = it.next();
            if (covers(finished, waiting)) {
                it.remove();
                queued.decrementAndGet();
                after.add(() -> waiting.job.settledBy(outcome));
            }
        }
    }

    private void failWaiting(Box box, List<Runnable> after) {
        while (!box.waiting.isEmpty()) {
            Entry waiting = box.waiting.pollFirst();
            queued.decrementAndGet();
            after.add(waiting.job::overloaded);
        }
    }

    /** 상자 잠금 안에서 부른다. 비었으면 닫고 지운다. 닫힌 상자를 받은 제출자는 새 상자로 다시 시도한다. */
    private void closeIfIdle(Long memberId, Box box) {
        if (box.running == null && box.pending == null && box.waiting.isEmpty() && !box.scheduled) {
            box.closed = true;
            if (boxes.remove(memberId, box)) {
                activeMembers.decrementAndGet();
            }
        }
    }

    /** 잰 시각이 둘 다 있으면 시각으로, 하나라도 없으면 도착 순서로 비교한다. */
    private static boolean isNewer(Entry entry, Entry newest) {
        if (newest == null) {
            return true;
        }
        Long order = entry.job.orderAtMs();
        Long newestOrder = newest.job.orderAtMs();
        if (order == null || newestOrder == null) {
            return entry.arrival > newest.arrival;
        }
        return order > newestOrder;
    }

    /** 끝난 작업이 밀린 항목보다 같거나 늦게 잰 것인가. */
    private static boolean covers(Entry finished, Entry waiting) {
        Long order = finished.job.orderAtMs();
        Long waitingOrder = waiting.job.orderAtMs();
        if (order == null || waitingOrder == null) {
            return finished.arrival > waiting.arrival;
        }
        return order >= waitingOrder;
    }

    public int trackedMembers() {
        return boxes.size();
    }

    public int queuedJobs() {
        return queued.get();
    }

    private record Entry(Job job, long arrival) {
    }

    private static final class Box {
        private Entry running;
        private Entry pending;
        private final ArrayDeque<Entry> waiting = new ArrayDeque<>();
        private boolean scheduled;
        private boolean closed;
        private long nextArrival;
    }
}
