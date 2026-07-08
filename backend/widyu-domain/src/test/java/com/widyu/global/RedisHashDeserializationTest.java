package com.widyu.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.auth.TemporaryMember;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.location.SeniorLocation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.data.redis.core.mapping.RedisMappingContext;

/**
 * @RedisHash 엔티티가 Redis 저장 후 다시 읽힐 때(역직렬화) 원본 값이 복원되는지 검증한다.
 * <p>
 * 파라미터가 있는 생성자만 가진 엔티티는 컴파일 시 -parameters 플래그가 없으면
 * "Parameter does not have a name" MappingException으로 역직렬화가 깨진다.
 * 이 테스트는 실제 실패 경로인 {@link MappingRedisConverter#read}를 write→read 왕복으로 재현한다.
 */
@DisplayName("@RedisHash 엔티티 Redis 역직렬화 테스트")
class RedisHashDeserializationTest {

    private final MappingRedisConverter converter = newConverter();

    private static MappingRedisConverter newConverter() {
        final MappingRedisConverter converter = new MappingRedisConverter(new RedisMappingContext());
        converter.afterPropertiesSet();
        return converter;
    }

    @Test
    @DisplayName("HeartRateResult를 저장 후 다시 읽으면 원본 값이 복원된다")
    void 심박결과_역직렬화() {
        // given
        final LocalDateTime measuredAt = LocalDateTime.of(2026, 7, 8, 23, 0, 0);
        final HeartRateResult origin = HeartRateResult.of(1L, HeartRateStatus.ANOMALY, 120, measuredAt);
        final RedisData sink = new RedisData();
        converter.write(origin, sink);

        // when
        final HeartRateResult read = converter.read(HeartRateResult.class, sink);

        // then
        assertThat(read.getMemberId()).isEqualTo(1L);
        assertThat(read.getStatus()).isEqualTo(HeartRateStatus.ANOMALY);
        assertThat(read.getHeartRate()).isEqualTo(120);
        assertThat(read.getMeasuredAt()).isEqualTo(measuredAt);
    }

    @Test
    @DisplayName("TemporaryMember를 저장 후 다시 읽으면 원본 값이 복원된다")
    void 임시회원_역직렬화() {
        // given
        final TemporaryMember origin = TemporaryMember.createTemporaryMember("홍길동", "01012345678");
        final RedisData sink = new RedisData();
        converter.write(origin, sink);

        // when
        final TemporaryMember read = converter.read(TemporaryMember.class, sink);

        // then
        assertThat(read.getId()).isEqualTo(origin.getId());
        assertThat(read.getName()).isEqualTo("홍길동");
        assertThat(read.getPhoneNumber()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("SeniorLocation을 저장 후 다시 읽으면 원본 값이 복원된다")
    void 시니어위치_역직렬화() {
        // given
        final SeniorLocation origin = SeniorLocation.of(1L, 37.5665, 126.9780);
        final RedisData sink = new RedisData();
        converter.write(origin, sink);

        // when
        final SeniorLocation read = converter.read(SeniorLocation.class, sink);

        // then
        assertThat(read.getSeniorId()).isEqualTo(1L);
        assertThat(read.getLatitude()).isEqualTo(37.5665);
        assertThat(read.getLongitude()).isEqualTo(126.9780);
    }
}
