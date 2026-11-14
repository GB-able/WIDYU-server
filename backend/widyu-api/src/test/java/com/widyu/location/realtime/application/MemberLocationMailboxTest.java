package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.widyu.location.realtime.application.MemberLocationMailbox.Outcome;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 회원별 위치 우편함의 상한, 교체, 밀린 항목 정리, 공정성, 정리 경쟁을 본다(LLD-0144). */
@DisplayName("회원별 위치 우편함")
class MemberLocationMailboxTest {

    @Test
    @DisplayName("더 늦게 잰 위치가 오면 최신 대기를 교체하고, 교체된 위치는 새 위치가 반영된 뒤에야 그 결과로 끝난다")
    void 최신_대기를_교체하고_반영_뒤에_밀린_위치를_끝낸다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        mailbox.submit(1L, job("t100", 100L, Outcome.APPLIED, events));
        mailbox.submit(1L, job("t200", 200L, Outcome.APPLIED, events));

        // when
        executor.runAll();

        // then
        assertThat(events).containsExactly("run:t200", "settled:t100:APPLIED");
        assertThat(mailbox.trackedMembers()).isZero();
        assertThat(mailbox.queuedJobs()).isZero();
    }

    @Test
    @DisplayName("실행 중인 위치보다 먼저 잰 위치와 같은 위치 재전송은 실행 중인 위치가 끝난 뒤 그 결과로 끝나고 다시 실행되지 않는다")
    void 실행_중보다_먼저_잰_위치는_실행_결과로_끝난다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        BlockingJob running = new BlockingJob("t200", 200L, Outcome.APPLIED, events);
        mailbox.submit(1L, running);
        Thread worker = new Thread(executor::runOne);
        worker.start();
        running.awaitStarted();

        // when
        mailbox.submit(1L, job("t100", 100L, Outcome.APPLIED, events));
        mailbox.submit(1L, job("t200-again", 200L, Outcome.APPLIED, events));
        running.release();
        join(worker);
        executor.runAll();

        // then
        assertThat(events).containsExactly(
                "run:t200", "settled:t100:APPLIED", "settled:t200-again:APPLIED");
        assertThat(mailbox.trackedMembers()).isZero();
    }

    @Test
    @DisplayName("밀린 항목은 대신할 위치가 실패하면 기다렸다가 다음 위치의 결과로 끝나고, 더 처리할 위치가 없으면 과부하로 끝난다")
    void 대신할_위치가_실패하면_다음_위치를_기다리거나_과부하로_끝난다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        mailbox.submit(1L, job("t100", 100L, Outcome.APPLIED, events));
        mailbox.submit(1L, job("t200", 200L, Outcome.FAILED, events));

        // when
        executor.runAll();

        // then
        assertThat(events).containsExactly("run:t200", "overloaded:t100");
        assertThat(events).doesNotContain("settled:t100:APPLIED");
        assertThat(mailbox.trackedMembers()).isZero();
        assertThat(mailbox.queuedJobs()).isZero();
    }

    @Test
    @DisplayName("실패한 위치 뒤에 더 늦은 위치가 와 있으면 밀린 항목은 그 위치가 반영된 뒤 끝난다")
    void 실패_뒤_더_늦은_위치가_반영되면_밀린_항목이_끝난다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        BlockingJob failing = new BlockingJob("t200", 200L, Outcome.FAILED, events);
        mailbox.submit(1L, failing);
        Thread worker = new Thread(executor::runOne);
        worker.start();
        failing.awaitStarted();
        mailbox.submit(1L, job("t150", 150L, Outcome.APPLIED, events));
        mailbox.submit(1L, job("t300", 300L, Outcome.APPLIED, events));

        // when
        failing.release();
        join(worker);
        executor.runAll();

        // then
        assertThat(events).containsExactly("run:t200", "run:t300", "settled:t150:APPLIED");
    }

    @Test
    @DisplayName("밀린 항목의 응답 전송이 예외를 던져도 다음 최신 위치의 차례 등록은 그대로 해 회원 처리가 멈추지 않는다")
    void 응답_전송_예외에도_다음_차례를_등록한다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        BlockingJob running = new BlockingJob("t200", 200L, Outcome.APPLIED, events);
        mailbox.submit(1L, running);
        Thread worker = new Thread(executor::runOne);
        worker.start();
        running.awaitStarted();
        mailbox.submit(1L, new RecordingJob("t100", 100L, Outcome.APPLIED, events) {
            @Override
            public void settledBy(Outcome result) {
                throw new IllegalStateException("send failed");
            }
        });
        mailbox.submit(1L, job("t300", 300L, Outcome.APPLIED, events));

        // when
        running.release();
        join(worker);
        executor.runAll();

        // then
        assertThat(events).containsExactly("run:t200", "run:t300");
        assertThat(mailbox.trackedMembers()).isZero();
        assertThat(mailbox.queuedJobs()).isZero();
    }

    @Test
    @DisplayName("거절로 끝난 위치는 밀린 항목도 거절로 끝낸다")
    void 거절은_밀린_항목도_거절로_끝낸다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        mailbox.submit(1L, job("t100", 100L, Outcome.APPLIED, events));
        mailbox.submit(1L, job("t200", 200L, Outcome.REJECTED, events));

        // when
        executor.runAll();

        // then
        assertThat(events).containsExactly("run:t200", "settled:t100:REJECTED");
    }

    @Test
    @DisplayName("회원별 밀린 항목이 상한을 넘으면 가장 먼저 들어온 항목을 과부하로 끝내고 최신 위치는 받는다")
    void 밀린_항목_상한을_넘으면_가장_오래된_항목을_과부하로_끝낸다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 2, 10, 100);
        List<String> events = new ArrayList<>();

        // when
        for (long t = 1; t <= 5; t++) {
            mailbox.submit(1L, job("t" + t, t, Outcome.APPLIED, events));
        }
        List<String> beforeRun = new ArrayList<>(events);
        executor.runAll();

        // then
        assertThat(beforeRun).containsExactly("overloaded:t1", "overloaded:t2");
        assertThat(events).containsExactly("overloaded:t1", "overloaded:t2",
                "run:t5", "settled:t3:APPLIED", "settled:t4:APPLIED");
        assertThat(mailbox.queuedJobs()).isZero();
    }

    @Test
    @DisplayName("전체 보관 상한과 활성 회원 상한을 넘으면 새 위치를 실행하지 않고 과부하로 끝낸다")
    void 전체_상한을_넘으면_과부하로_끝낸다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox byQueued = new MemberLocationMailbox(executor, 4, 10, 2);
        MemberLocationMailbox byMembers = new MemberLocationMailbox(executor, 4, 1, 100);
        List<String> events = new ArrayList<>();

        // when
        byQueued.submit(1L, job("a1", 1L, Outcome.APPLIED, events));
        byQueued.submit(2L, job("b1", 1L, Outcome.APPLIED, events));
        byQueued.submit(3L, job("c1", 1L, Outcome.APPLIED, events));
        byMembers.submit(7L, job("x1", 1L, Outcome.APPLIED, events));
        byMembers.submit(8L, job("y1", 1L, Outcome.APPLIED, events));

        // then
        assertThat(events).containsExactly("overloaded:c1", "overloaded:y1");
        assertThat(byQueued.queuedJobs()).isEqualTo(2);
        assertThat(byMembers.trackedMembers()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 회원에게 할 일이 계속 남아도 실행기 대기열 맨 뒤로 다시 들어가 다른 회원과 번갈아 실행된다")
    void 회원_사이를_번갈아_실행한다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        BlockingJob first = new BlockingJob("a1", 1L, Outcome.APPLIED, events);
        mailbox.submit(1L, first);
        Thread worker = new Thread(executor::runOne);
        worker.start();
        first.awaitStarted();
        mailbox.submit(1L, job("a2", 2L, Outcome.APPLIED, events));
        mailbox.submit(2L, job("b1", 1L, Outcome.APPLIED, events));

        // when
        first.release();
        join(worker);
        executor.runAll();

        // then
        assertThat(events).containsExactly("run:a1", "run:b1", "run:a2");
    }

    @Test
    @DisplayName("잰 시각이 없는 구형 위치는 도착 순서로 비교해 나중 위치가 앞선 대기를 교체한다")
    void 구형_위치는_도착_순서로_비교한다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();
        mailbox.submit(1L, job("first", null, Outcome.APPLIED, events));
        mailbox.submit(1L, job("second", null, Outcome.APPLIED, events));

        // when
        executor.runAll();

        // then
        assertThat(events).containsExactly("run:second", "settled:first:APPLIED");
    }

    @Test
    @DisplayName("실행기가 종료돼 차례를 거절하면 남은 위치를 과부하로 끝내고 회원 항목을 지운다")
    void 실행기_거절이면_과부하로_끝내고_정리한다() {
        // given
        ManualExecutor executor = new ManualExecutor();
        executor.rejecting = true;
        MemberLocationMailbox mailbox = new MemberLocationMailbox(executor, 4, 10, 100);
        List<String> events = new ArrayList<>();

        // when
        mailbox.submit(1L, job("t1", 1L, Outcome.APPLIED, events));

        // then
        assertThat(events).containsExactly("overloaded:t1");
        assertThat(mailbox.trackedMembers()).isZero();
        assertThat(mailbox.queuedJobs()).isZero();
    }

    @Test
    @DisplayName("같은 회원 위치를 여러 수신 스레드가 동시에 넣고 처리와 정리가 겹쳐도 모든 위치가 한 번씩 끝나고 항목이 남지 않는다")
    void 동시에_넣고_정리가_겹쳐도_모든_위치가_한_번씩_끝난다() throws Exception {
        // given
        ExecutorService workers = Executors.newFixedThreadPool(4);
        ExecutorService senders = Executors.newFixedThreadPool(8);
        MemberLocationMailbox mailbox = new MemberLocationMailbox(workers, 1_000, 10, 100_000);
        CountingJob.Counter counter = new CountingJob.Counter();
        int perSender = 500;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int s = 0; s < 8; s++) {
                int sender = s;
                futures.add(senders.submit(() -> {
                    start.await();
                    for (int i = 0; i < perSender; i++) {
                        mailbox.submit((long) (i % 3), new CountingJob((long) i * 8 + sender, counter));
                    }
                    return null;
                }));
            }

            // when
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            long deadline = System.currentTimeMillis() + 10_000;
            // 마지막 작업이 끝났다고 센 직후에도 우편함 정리(작업 수 감소, 회원 항목 삭제)가 남아 있을 수 있어 함께 기다린다.
            while ((counter.finished() < 8 * perSender || mailbox.trackedMembers() > 0 || mailbox.queuedJobs() > 0)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            // then
            assertThat(counter.finished()).isEqualTo(8 * perSender);
            assertThat(counter.duplicates()).isZero();
            assertThat(mailbox.trackedMembers()).isZero();
            assertThat(mailbox.queuedJobs()).isZero();
        } finally {
            senders.shutdownNow();
            workers.shutdownNow();
        }
    }

    @Test
    @DisplayName("상한이 음수이거나 회원, 전체 상한이 0이면 시작할 때 거절한다")
    void 잘못된_상한은_거절한다() {
        assertThatThrownBy(() -> new MemberLocationMailbox(new ManualExecutor(), -1, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemberLocationMailbox(new ManualExecutor(), 1, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemberLocationMailbox(new ManualExecutor(), 1, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RecordingJob job(String name, Long orderAtMs, Outcome outcome, List<String> events) {
        return new RecordingJob(name, orderAtMs, outcome, events);
    }

    private static void join(Thread thread) {
        try {
            thread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 넣은 차례를 손으로 하나씩 실행한다. 실행 순서(공정성)를 결정적으로 본다. */
    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean rejecting;

        @Override
        public synchronized void execute(Runnable task) {
            if (rejecting) {
                throw new RejectedExecutionException("shutdown");
            }
            tasks.add(task);
        }

        void runOne() {
            Runnable task;
            synchronized (this) {
                task = tasks.poll();
            }
            if (task != null) {
                task.run();
            }
        }

        void runAll() {
            while (true) {
                Runnable task;
                synchronized (this) {
                    task = tasks.poll();
                }
                if (task == null) {
                    return;
                }
                task.run();
            }
        }
    }

    private static class RecordingJob implements MemberLocationMailbox.Job {
        final String name;
        final Long orderAtMs;
        final Outcome outcome;
        final List<String> events;

        RecordingJob(String name, Long orderAtMs, Outcome outcome, List<String> events) {
            this.name = name;
            this.orderAtMs = orderAtMs;
            this.outcome = outcome;
            this.events = events;
        }

        @Override
        public Long orderAtMs() {
            return orderAtMs;
        }

        @Override
        public Outcome run() {
            record("run:" + name);
            return outcome;
        }

        @Override
        public void settledBy(Outcome result) {
            record("settled:" + name + ":" + result);
        }

        @Override
        public void overloaded() {
            record("overloaded:" + name);
        }

        void record(String event) {
            synchronized (events) {
                events.add(event);
            }
        }
    }

    /** 실행에 들어간 것을 알리고 풀어 줄 때까지 붙잡힌다. 실행 중에 다른 위치가 들어오는 상황을 만든다. */
    private static final class BlockingJob extends RecordingJob {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        BlockingJob(String name, Long orderAtMs, Outcome outcome, List<String> events) {
            super(name, orderAtMs, outcome, events);
        }

        @Override
        public Outcome run() {
            record("run:" + name);
            started.countDown();
            try {
                released.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return outcome;
        }

        void awaitStarted() {
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void release() {
            released.countDown();
        }
    }

    /** 위치마다 끝난 횟수를 센다. 실행, 밀린 항목 정리, 과부하 중 하나로 정확히 한 번 끝나야 한다. */
    private static final class CountingJob implements MemberLocationMailbox.Job {
        private final long orderAtMs;
        private final Counter counter;

        CountingJob(long orderAtMs, Counter counter) {
            this.orderAtMs = orderAtMs;
            this.counter = counter;
        }

        @Override
        public Long orderAtMs() {
            return orderAtMs;
        }

        @Override
        public Outcome run() {
            counter.finish(this);
            return Outcome.APPLIED;
        }

        @Override
        public void settledBy(Outcome outcome) {
            counter.finish(this);
        }

        @Override
        public void overloaded() {
            counter.finish(this);
        }

        static final class Counter {
            private final java.util.Set<CountingJob> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
            private final java.util.concurrent.atomic.AtomicInteger finished = new java.util.concurrent.atomic.AtomicInteger();
            private final java.util.concurrent.atomic.AtomicInteger duplicates = new java.util.concurrent.atomic.AtomicInteger();

            void finish(CountingJob job) {
                if (!seen.add(job)) {
                    duplicates.incrementAndGet();
                }
                finished.incrementAndGet();
            }

            int finished() {
                return finished.get();
            }

            int duplicates() {
                return duplicates.get();
            }
        }
    }
}
