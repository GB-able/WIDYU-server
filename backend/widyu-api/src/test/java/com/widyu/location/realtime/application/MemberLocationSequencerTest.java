package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원별 위치 처리 순서 잠금")
class MemberLocationSequencerTest {

    @Test
    @DisplayName("같은 회원의 대기 수가 상한을 넘으면 기다리지 않고 바로 과부하 예외를 던진다")
    void 대기_수가_상한을_넘으면_바로_과부하_예외를_던진다() throws Exception {
        // given
        MemberLocationSequencer sequencer = new MemberLocationSequencer(1, 5_000);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> sequencer.run(1L, () -> {
                holding.countDown();
                await(release);
                return null;
            }));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();
            Future<String> waiter = pool.submit(() -> sequencer.run(1L, () -> "waited"));
            Thread.sleep(100);

            // when & then
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> sequencer.run(1L, () -> "rejected"))
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
            pool.submit(() -> sequencer.run(1L, () -> {
                holding.countDown();
                await(release);
                return null;
            }));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();
            boolean[] ran = {false};

            // when & then
            assertThatThrownBy(() -> sequencer.run(1L, () -> {
                ran[0] = true;
                return null;
            })).isInstanceOf(LocationSequenceBusyException.class);
            assertThat(ran[0]).isFalse();
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
        sequencer.run(1L, () -> "done");
        assertThatThrownBy(() -> sequencer.run(2L, () -> {
            throw new IllegalStateException("Redis 장애");
        })).isInstanceOf(IllegalStateException.class);

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
