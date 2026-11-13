package com.widyu.location.realtime.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocationAckResponse 직렬화 단위 테스트")
class LocationAckResponseTest {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("v2 위치 ACK를 직렬화하면 기존 필드를 그대로 두고 식별자와 결과를 같은 단계에 덧붙인다")
    void v2_위치_ACK를_직렬화하면_기존_필드와_식별자를_같은_단계에_싣는다() throws Exception {
        // given
        LocationUpdateResponse location = LocationUpdateResponse.of(
                1L, "시니어", null, 37.5551, 126.9707, LocalDateTime.now(), "OTHER", null,
                LocationFreshness.of(1_000L, 2_000L, LocationSource.LATEST, 61_000L));
        LocationAckResponse ack = LocationAckResponse.of(location, request(), LocationAckResult.STORED);

        // when
        JsonNode body = JSON.readTree(JSON.writeValueAsString(ack));

        // then
        assertThat(body.get("memberId").asLong()).isEqualTo(1L);
        assertThat(body.get("latitude").asDouble()).isEqualTo(37.5551);
        assertThat(body.has("location")).isFalse();
        assertThat(body.get("deviceId").asText()).isEqualTo("ph-9c1");
        assertThat(body.get("sessionId").asText()).isEqualTo("s-2026");
        assertThat(body.get("seq").asLong()).isEqualTo(5120L);
        assertThat(body.get("result").asText()).isEqualTo("STORED");
        assertThat(body.get("measuredAtMs").asLong()).isEqualTo(1_000L);
        assertThat(body.get("receivedAtMs").asLong()).isEqualTo(2_000L);
        assertThat(body.get("locationSource").asText()).isEqualTo("LATEST");
        assertThat(body.get("ageSeconds").asLong()).isEqualTo(60L);
    }

    @Test
    @DisplayName("거절 ACK를 직렬화하면 위치 필드 없이 식별자와 REJECTED만 싣는다")
    void 거절_ACK를_직렬화하면_식별자와_REJECTED만_싣는다() throws Exception {
        // given
        LocationAckResponse ack = LocationAckResponse.rejected(request());

        // when
        JsonNode body = JSON.readTree(JSON.writeValueAsString(ack));

        // then
        assertThat(body.has("memberId")).isFalse();
        assertThat(body.get("seq").asLong()).isEqualTo(5120L);
        assertThat(body.get("result").asText()).isEqualTo("REJECTED");
    }

    private static LocationUpdateRequest request() {
        return new LocationUpdateRequest(
                1L, null, null, null, 2, "ph-9c1", "s-2026", 5120L,
                null, null, null,
                37.5551, 126.9707, 8.5, 0.9, 0.5, 212.0, 41.2, "fused", false, 1_760_000_000_123L, "move");
    }
}
