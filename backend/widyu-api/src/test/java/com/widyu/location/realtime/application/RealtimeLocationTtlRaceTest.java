package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.lenient;

import com.widyu.location.SeniorLocation;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.raw.application.LocationFixService;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 위치 TTL 갱신과 위치 수신이 겹칠 때 최신 위치가 과거로 돌아가지 않는지 본다(LLD-0140). 갱신이 위치를 읽은 직후
 * 새 위치가 저장되는 순서를 테스트가 만든다. 갱신이 읽어 둔 위치를 다시 저장하면 새 위치를 덮는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("위치 TTL 갱신과 위치 수신 경쟁")
class RealtimeLocationTtlRaceTest {

    @Mock private SeniorLocationRepository seniorLocationRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private ParentLocationRepository parentLocationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SafeZoneAlertService safeZoneAlertService;
    @Mock private LocationFixService locationFixService;
    @Mock private LocationAccessLogService locationAccessLogService;
    @Mock private MemberRepository memberRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Spy private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Spy private MemberLocationSequencer sequencer = new MemberLocationSequencer(4, 3_000);

    @InjectMocks
    private RealtimeLocationService realtimeLocationService;

    @Test
    @DisplayName("TTL 갱신이 위치를 읽은 뒤 새 위치가 저장돼도 갱신이 끝나면 최신 위치는 새 위치다")
    void TTL_갱신이_읽은_뒤_새_위치가_와도_최신_위치는_새_위치다() {
        // given
        long earlier = 1_000_000L;
        long later = earlier + 5_000L;
        Map<Long, SeniorLocation> redis = new ConcurrentHashMap<>();
        redis.put(1L, SeniorLocation.of(1L, 37.50, 127.0, earlier));
        // 갱신이 위치 목록을 읽어 간 직후 위치 수신이 새 위치를 저장한다.
        willAnswer(invocation -> {
            List<SeniorLocation> snapshot = List.copyOf(redis.values());
            redis.put(1L, SeniorLocation.of(1L, 37.60, 127.0, later));
            return snapshot;
        }).given(seniorLocationRepository).findAll();
        lenient().when(seniorLocationRepository.save(any(SeniorLocation.class))).thenAnswer(invocation -> {
            SeniorLocation location = invocation.getArgument(0);
            redis.put(location.getSeniorId(), location);
            return location;
        });
        given(memberRepository.findActiveIdsIn(List.of(1L))).willReturn(Set.of(1L));
        lenient().when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // when
        realtimeLocationService.refreshLocationTtl();

        // then
        assertThat(redis.get(1L).getMeasuredAtMs()).isEqualTo(later);
    }
}
