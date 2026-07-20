package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.fcm.event.safezone.dto.SafeZoneExitEvent;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("SafeZoneAlertService 단위 테스트")
class SafeZoneAlertServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SafeZoneAlertService safeZoneAlertService;

    @Test
    @DisplayName("안전구역에 재진입하면 알림 플래그를 삭제한다")
    void 안전구역에_재진입하면_알림_플래그를_삭제한다() {
        // when
        safeZoneAlertService.handleSafeZoneTransition(1L, null, "HOME");

        // then
        then(redisTemplate).should().delete("safezone:alert:1");
        then(eventPublisher).should(never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("안전구역 밖에서 계속 머무르면 이벤트를 발행하지 않는다")
    void 안전구역_밖에서_계속_머무르면_이벤트를_발행하지_않는다() {
        // when
        safeZoneAlertService.handleSafeZoneTransition(1L, null, null);

        // then
        then(redisTemplate).should(never()).opsForValue();
        then(eventPublisher).should(never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("안전구역 이탈 플래그 생성에 성공하면 이벤트를 발행한다")
    void 안전구역_이탈_플래그_생성에_성공하면_이벤트를_발행한다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("safezone:alert:1"),
                eq(true),
                eq(1800L),
                eq(TimeUnit.SECONDS)
        )).willReturn(true);
        ArgumentCaptor<SafeZoneExitEvent> eventCaptor = ArgumentCaptor.forClass(SafeZoneExitEvent.class);

        // when
        safeZoneAlertService.handleSafeZoneTransition(1L, "HOME", null);

        // then
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        SafeZoneExitEvent event = eventCaptor.getValue();
        assertThat(event.seniorMemberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("안전구역 이탈 플래그가 이미 있으면 이벤트를 발행하지 않는다")
    void 안전구역_이탈_플래그가_이미_있으면_이벤트를_발행하지_않는다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
                eq("safezone:alert:1"),
                eq(true),
                eq(1800L),
                eq(TimeUnit.SECONDS)
        )).willReturn(false);

        // when
        safeZoneAlertService.handleSafeZoneTransition(1L, "HOME", null);

        // then
        then(eventPublisher).should(never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
