package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원별 위치 처리 순서 잠금")
class MemberLocationSequencerTest {

    private static final Supplier<String> SUPERSEDED = () -> "superseded";

    @Test
    @DisplayName("같은 회원의 대기 수가 상한을 넘으면 기다리지 않고 바로 과부하 예외를 던진다")
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
            Future<String> waiter = pool.submit(() -> sequencer.run(1L, 300L, () -> "waited", SUPERSEDED));
            Thread.sleep(100);

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
    @DisplayName("대기 시간 안에 차례가 오지 않으면 실행하지 않고 과부하 예외를 던진다")
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
    @DisplayName("더 늦게 잰 위치가 처리 중이면 먼저 잰 위치는 기다리지 않고 밀린 것으로 끝난다")
    void 더_최근_위치가_처리_중이면_먼저_잰_위치는_바로_밀린다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 5_000);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> holder = pool.submit(() -> sequencer.run(1L, 500L, () -> {
                holding.countDown();
                await(release);
                return "applied";
            }, SUPERSEDED));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();

            // when
            long startedAt = System.nanoTime();
            String older = sequencer.run(1L, 400L, () -> "ran", SUPERSEDED);

            // then
            assertThat(older).isEqualTo("superseded");
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(500);
            release.countDown();
            assertThat(holder.get(2, TimeUnit.SECONDS)).isEqualTo("applied");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("대기가 가득 차도 지금까지보다 늦게 잰 위치는 들어오고, 그보다 먼저 잰 대기자는 밀려 빠진다")
    void 대기가_가득_차도_가장_최근_위치는_들어오고_먼저_잰_대기자는_빠진다() throws Exception {
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
            Future<String> olderWaiter = pool.submit(() -> sequencer.run(1L, 200L, () -> "older", SUPERSEDED));
            Thread.sleep(100);

            // when
            Future<String> newest = pool.submit(() -> sequencer.run(1L, 300L, () -> "newest", SUPERSEDED));

            // then
            assertThat(olderWaiter.get(2, TimeUnit.SECONDS)).isEqualTo("superseded");
            release.countDown();
            assertThat(holder.get(2, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(newest.get(2, TimeUnit.SECONDS)).isEqualTo("newest");
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
            Future<String> resent = pool.submit(() -> sequencer.run(1L, 500L, () -> "resent", SUPERSEDED));
            Thread.sleep(100);
            release.countDown();

            // then
            assertThat(resent.get(2, TimeUnit.SECONDS)).isEqualTo("resent");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("처리가 끝나면 예외가 나도 회원 항목을 지워 잠금이 쌓이지 않는다")
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

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
