package com.widyu.location.stay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.location.stay.SafeZoneAlertState;
import com.widyu.location.stay.SeniorStayState;
import com.widyu.location.stay.repository.SeniorStayStateRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("예전 Redis 체류 상태 가져오기")
class LegacyStayStateImporterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private SeniorStayStateRepository repository;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private LegacyStayStateImporter importer;

    @Test
    @DisplayName("체류 키와 진행 중인 이탈 묶음을 옮기고 남은 TTL로 묶음 끝 시각을 정한다")
    void 체류와_진행_중인_묶음을_옮긴다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("location:stay:7")).willReturn(stayMap(37.5, 127.0, "HOME", "집"));
        given(valueOperations.get("safezone:alert:7")).willReturn("EXITED");
        given(redisTemplate.getExpire("safezone:alert:7", TimeUnit.SECONDS)).willReturn(600L);
        given(repository.existsById(7L)).willReturn(false);

        // when
        LegacyStayStateImporter.Result result = importer.importKeys(List.of("location:stay:7"), NOW);

        // then
        assertThat(result.imported()).isEqualTo(1);
        ArgumentCaptor<SeniorStayState> saved = ArgumentCaptor.forClass(SeniorStayState.class);
        then(repository).should().save(saved.capture());
        assertThat(saved.getValue().getLocationType()).isEqualTo("HOME");
        assertThat(saved.getValue().getStayStartedAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 9, 0));
        assertThat(saved.getValue().getAlertState()).isEqualTo(SafeZoneAlertState.EXITED);
        assertThat(saved.getValue().getAlertUntil()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    @DisplayName("DB에 이미 상태가 있으면 덮지 않고, 좌표가 빠진 예전 값은 옮기지 않는다")
    void 이미_있으면_덮지_않고_깨진_값은_건너뛴다() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("location:stay:7")).willReturn(stayMap(37.5, 127.0, null, null));
        given(valueOperations.get("safezone:alert:7")).willReturn(null);
        given(valueOperations.get("location:stay:8")).willReturn(stayMap(null, 127.0, null, null));
        given(repository.existsById(7L)).willReturn(true);

        // when
        LegacyStayStateImporter.Result result =
                importer.importKeys(List.of("location:stay:7", "location:stay:8"), NOW);

        // then
        assertThat(result.existing()).isEqualTo(1);
        assertThat(result.invalid()).isEqualTo(1);
        then(repository).should(never()).save(any());
    }

    private static Map<String, Object> stayMap(Double latitude, Double longitude, String type, String name) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("latitude", latitude);
        raw.put("longitude", longitude);
        raw.put("startTime", "2026-11-13T09:00:00");
        raw.put("locationType", type);
        raw.put("locationName", name);
        return raw;
    }
}
