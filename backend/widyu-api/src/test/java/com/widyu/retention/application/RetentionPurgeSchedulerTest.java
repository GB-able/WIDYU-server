package com.widyu.retention.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("보관 기간 정리 스케줄러 등록 테스트")
class RetentionPurgeSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RetentionPurgeService.class, () -> mock(RetentionPurgeService.class))
            .withUserConfiguration(RetentionPurgeScheduler.class);

    @Test
    @DisplayName("정리 작업을 끄면 스케줄러 빈을 등록하지 않는다")
    void 정리_작업을_끄면_스케줄러_빈을_등록하지_않는다() {
        contextRunner.withPropertyValues("retention.purge.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RetentionPurgeScheduler.class));
    }

    @Test
    @DisplayName("정리 작업을 켜면 스케줄러 빈을 등록한다")
    void 정리_작업을_켜면_스케줄러_빈을_등록한다() {
        contextRunner.withPropertyValues("retention.purge.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RetentionPurgeScheduler.class));
    }
}
