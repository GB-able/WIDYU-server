package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원별 위치 처리 순서 잠금")
class MemberLocationSequencerTest {

    private static final Supplier<String> SUPERSEDED = () -> "superseded";

    @Test
    @DisplayName("대기 수나 대기 시간을 음수로 설정하면 시작 시 예외가 발생한다")
    void 음수_대기_설정을_거절한다() {
        assertThatThrownBy(() -> new MemberLocationSequencer(-1, 3_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-waiting");
        assertThatThrownBy(() -> new MemberLocationSequencer(4, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wait-timeout-ms");
    }

    @Test
    @DisplayName("대기 수와 시간을 0으로 설정해도 빈 잠금이면 즉시 처리한다")
    void 대기하지_않는_설정으로_빈_잠금을_사용한다() {
        MemberLocationSequencer sequencer = new MemberLocationSequencer(0, 0);

        assertThat(sequencer.run(1L, 100L, () -> "done", SUPERSEDED)).isEqualTo("done");
        assertThat(sequencer.trackedMembers()).isZero();
    }

    @Test
    @DisplayName("대기 시간이 0이고 다른 요청이 잠금을 사용하면 즉시 과부하 예외가 발생한다")
    void 대기_시간이_0이면_사용_중인_잠금을_기다리지_않는다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 0);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> holder = pool.submit(() -> sequencer.run(1L, 100L, () -> {
                holding.countDown();
                await(release);
                return "held";
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();

            // when & then
            assertThatThrownBy(() -> sequencer.run(1L, 100L, () -> "unexpected", SUPERSEDED))
                    .isInstanceOf(LocationSequenceBusyException.class);
            release.countDown();
            assertThat(holder.get(2, TimeUnit.SECONDS)).isEqualTo("held");
            assertThat(sequencer.trackedMembers()).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("같은 회원의 대기 수가 상한을 넘으면 즉시 과부하 예외가 발생한다")
    void 대기_수가_상한을_넘으면_바로_과부하_예외를_던진다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(1, 5_000);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> sequencer.run(1L, 300L, () -> {
                holding.countDown();
                await(release);
                return "held";
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();
            Future<String> waiter = submitWaiting(pool,
                    () -> sequencer.run(1L, 300L, () -> "waited", SUPERSEDED));

            // when & then
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> sequencer.run(1L, 300L, () -> "rejected", SUPERSEDED))
                    .isInstanceOf(LocationSequenceBusyException.class);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(1_000);
            release.countDown();
            holder.get(2, TimeUnit.SECONDS);
            assertThat(waiter.get(2, TimeUnit.SECONDS)).isEqualTo("waited");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("대기 시간 안에 차례가 오지 않으면 실행 없이 과부하 예외가 발생한다")
    void 대기_시간을_넘기면_실행하지_않고_과부하_예외를_던진다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 100);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> sequencer.run(1L, 100L, () -> {
                holding.countDown();
                await(release);
                return "held";
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();
            boolean[] ran = {false};

            // when & then
            assertThatThrownBy(() -> sequencer.run(1L, 200L, () -> {
                ran[0] = true;
                return "ran";
            }, SUPERSEDED)).isInstanceOf(LocationSequenceBusyException.class);
            assertThat(ran[0]).isFalse();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("더 늦게 잰 위치가 처리 중이면 먼저 잰 위치는 자기 처리 없이 그 위치가 끝나기를 기다려 밀린 것으로 끝난다")
    void 더_최근_위치가_끝나면_먼저_잰_위치는_밀린_것으로_끝난다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 5_000);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> holder = pool.submit(() -> sequencer.run(1L, 500L, () -> {
                holding.countDown();
                await(release);
                return "applied";
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();
            boolean[] olderRan = {false};

            // when
            Future<String> older = submitWaiting(pool, () -> sequencer.run(1L, 400L, () -> {
                olderRan[0] = true;
                return "ran";
            }, SUPERSEDED));
            assertThat(older.isDone()).isFalse();
            release.countDown();

            // then
            assertThat(older.get(2, TimeUnit.SECONDS)).isEqualTo("superseded");
            assertThat(olderRan[0]).isFalse();
            assertThat(holder.get(2, TimeUnit.SECONDS)).isEqualTo("applied");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("더 늦게 잰 위치의 실시간 처리가 실패하면 밀린 위치도 성공으로 끝나지 않고 과부하 예외가 된다")
    void 더_최근_위치가_실패하면_밀린_위치도_성공하지_않는다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 300);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> holder = pool.submit(() -> sequencer.run(1L, 500L, () -> {
                holding.countDown();
                await(release);
                throw new IllegalStateException("Redis 장애");
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();

            // when
            Future<String> older = submitWaiting(pool,
                    () -> sequencer.run(1L, 400L, () -> "ran", SUPERSEDED));
            release.countDown();

            // then
            assertThatThrownBy(() -> older.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(LocationSequenceBusyException.class);
            assertThatThrownBy(() -> holder.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("대기가 가득 차도 지금까지보다 늦게 잰 위치는 들어오고, 그보다 먼저 잰 대기자는 그 위치가 반영된 뒤 밀린 것으로 끝난다")
    void 대기가_가득_차도_가장_최근_위치는_들어오고_먼저_잰_대기자는_밀린다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(1, 5_000);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<String> holder = pool.submit(() -> sequencer.run(1L, 100L, () -> {
                holding.countDown();
                await(release);
                return "first";
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();
            Future<String> olderWaiter = submitWaiting(pool,
                    () -> sequencer.run(1L, 200L, () -> "older", SUPERSEDED));

            // when
            Future<String> newest = submitWaiting(pool,
                    () -> sequencer.run(1L, 300L, () -> "newest", SUPERSEDED));
            release.countDown();

            // then
            assertThat(holder.get(2, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(newest.get(2, TimeUnit.SECONDS)).isEqualTo("newest");
            assertThat(olderWaiter.get(2, TimeUnit.SECONDS)).isEqualTo("superseded");
            assertThat(sequencer.trackedMembers()).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("같은 측정 시각의 재전송은 밀리지 않고 차례를 기다린다")
    void 같은_시각의_재전송은_밀리지_않는다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 5_000);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> sequencer.run(1L, 500L, () -> {
                holding.countDown();
                await(release);
                return "original";
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();

            // when
            Future<String> resent = submitWaiting(pool,
                    () -> sequencer.run(1L, 500L, () -> "resent", SUPERSEDED));
            release.countDown();

            // then
            assertThat(resent.get(2, TimeUnit.SECONDS)).isEqualTo("resent");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("처리가 끝나면 예외 여부와 무관하게 회원 잠금 항목을 제거한다")
    void 처리가_끝나면_회원_항목을_지운다() {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 100);

        // when
        sequencer.run(1L, null, () -> "done", SUPERSEDED);
        assertThatThrownBy(() -> sequencer.run(2L, 100L, () -> {
            throw new IllegalStateException("Redis 장애");
        }, SUPERSEDED)).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(sequencer.trackedMembers()).isZero();
    }

    private static Future<String> submitWaiting(ExecutorService pool, Supplier<String> work) {
        AtomicReference<Thread> worker = new AtomicReference<>();
        Future<String> future = pool.submit(() -> {
            worker.set(Thread.currentThread());
            return work.get();
        });
        org.awaitility.Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(worker.get()).isNotNull();
            assertThat(future).isNotDone();
            assertThat(worker.get().getState()).isEqualTo(Thread.State.TIMED_WAITING);
        });
        return future;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
