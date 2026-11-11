package com.widyu.location.access.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.location.access.repository.LocationAccessLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationAccessDigestScheduler 위치 열람 요약 통보 스케줄러 단위 테스트")
class LocationAccessDigestSchedulerTest {

    @Mock private LocationAccessLogRepository locationAccessLogRepository;
    @Mock private LocationAccessDigestSender locationAccessDigestSender;

    @InjectMocks
    private LocationAccessDigestScheduler locationAccessDigestScheduler;

    @Test
    @DisplayName("미통보 기록이 있으면 시니어마다 독립된 발송 트랜잭션을 호출한다")
    void 미통보_기록은_시니어마다_독립적으로_통보한다() {
        // given
        given(locationAccessLogRepository.findSeniorIdsWithUnnotified()).willReturn(List.of(7L, 8L));

        // when
        locationAccessDigestScheduler.sendDigest();

        // then
        then(locationAccessDigestSender).should().send(eq(7L), any(LocalDateTime.class));
        then(locationAccessDigestSender).should().send(eq(8L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("한 시니어 통보가 실패해도 다른 시니어 통보를 계속한다")
    void 한_시니어_실패가_다른_시니어를_막지_않는다() {
        // given
        given(locationAccessLogRepository.findSeniorIdsWithUnnotified()).willReturn(List.of(7L, 8L));
        willThrow(new IllegalStateException("FCM 장애"))
                .given(locationAccessDigestSender).send(eq(7L), any(LocalDateTime.class));

        // when
        locationAccessDigestScheduler.sendDigest();

        // then
        then(locationAccessDigestSender).should().send(eq(8L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("미통보 기록이 없으면 발송 트랜잭션을 호출하지 않는다")
    void 미통보_기록이_없으면_아무것도_하지_않는다() {
        // given
        given(locationAccessLogRepository.findSeniorIdsWithUnnotified()).willReturn(List.of());

        // when
        locationAccessDigestScheduler.sendDigest();

        // then
        then(locationAccessDigestSender).should(never()).send(any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("서버 시작 뒤 한 번 돌면 남아 있던 미통보 기록을 시니어마다 요약 통보한다")
    void 시작_시_한_번_남은_미통보_기록을_통보한다() {
        // given
        given(locationAccessLogRepository.findSeniorIdsWithUnnotified()).willReturn(List.of(7L));

        // when
        locationAccessDigestScheduler.flushOnStartup();

        // then
        then(locationAccessDigestSender).should().send(eq(7L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("시작 시 요약은 반복 주기 없이 initialDelay만 가진 1회 작업으로 등록된다")
    void 시작_시_요약은_1회_작업이다() throws NoSuchMethodException {
        // given & when
        Scheduled scheduled = LocationAccessDigestScheduler.class
                .getMethod("flushOnStartup")
                .getAnnotation(Scheduled.class);

        // then
        assertThat(scheduled.initialDelay()).isPositive();
        assertThat(scheduled.cron()).isEmpty();
        assertThat(scheduled.fixedDelay()).isNegative();
        assertThat(scheduled.fixedRate()).isNegative();
        assertThat(scheduled.fixedDelayString()).isEmpty();
        assertThat(scheduled.fixedRateString()).isEmpty();
    }

    @Test
    @DisplayName("운영값을 따로 주지 않으면 정기 요약은 꺼지고 반복 조회를 합치는 쿨다운 설정은 없다")
    void 기본값은_정기_요약_꺼짐이고_쿨다운_설정이_없다() {
        // given
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        // 호스트 환경변수를 읽지 않도록 빈 환경에서 자리표시자를 푼다.
        MockEnvironment environment = new MockEnvironment();

        // when
        String digestCron = environment.resolvePlaceholders(
                properties.getProperty("location-access.digest-cron"));

        // then
        assertThat(digestCron).isEqualTo(Scheduled.CRON_DISABLED);
        assertThat(properties).doesNotContainKey("location-access.immediate-cooldown-min");
    }
}
