package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원별 위치 처리 순서 잠금")
class MemberLocationSequencerTest {

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

        assertThat(sequencer.run(1L, () -> "done")).isEqualTo("done");
        assertThat(sequencer.trackedMembers()).isZero();
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
            Future<?> holder = pool.submit(() -> sequencer.run(1L, () -> {
                holding.countDown();
                await(release);
                return null;
            }));
            assertThat(holding.await(2, TimeUnit.SECONDS)).isTrue();
            AtomicReference<Thread> waiterThread = new AtomicReference<>();
            Future<String> waiter = pool.submit(() -> {
                waiterThread.set(Thread.currentThread());
                return sequencer.run(1L, () -> "waited");
            });
            org.awaitility.Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(waiterThread.get()).isNotNull();
                assertThat(waiterThread.get().getState()).isEqualTo(Thread.State.TIMED_WAITING);
            });

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
    @DisplayName("대기 시간 안에 차례가 오지 않으면 실행 없이 과부하 예외가 발생한다")
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
    @DisplayName("처리가 끝나면 예외 여부와 무관하게 회원 잠금 항목을 제거한다")
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
