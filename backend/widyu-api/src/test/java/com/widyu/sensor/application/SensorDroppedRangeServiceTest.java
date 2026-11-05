package com.widyu.sensor.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.sensor.SensorDroppedRange;
import com.widyu.sensor.dto.response.SensorBatchResult;
import com.widyu.sensor.dto.response.SensorBatchResultResponse;
import com.widyu.sensor.repository.SensorDroppedRangeRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SensorDroppedRangeService 단위 테스트")
class SensorDroppedRangeServiceTest {

    private static final Long MEMBER_ID = 42L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock private SensorDroppedRangeRepository sensorDroppedRangeRepository;

    @Test
    @DisplayName("버린 구간 기록을 받으면 원문과 함께 저장하고 STORED를 돌려준다")
    void 버린_구간_기록을_받으면_원문과_함께_저장한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"queue_overflow\"").getBytes(UTF_8);
        given(sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(MEMBER_ID, "s-01", 10L))
                .willReturn(false);

        // when
        SensorBatchResultResponse response = service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L);

        // then
        assertThat(response.result()).isEqualTo(SensorBatchResult.STORED);
        assertThat(response.batchId()).isNull();
        ArgumentCaptor<SensorDroppedRange> saved = ArgumentCaptor.forClass(SensorDroppedRange.class);
        then(sensorDroppedRangeRepository).should().save(saved.capture());
        assertThat(saved.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getValue().getSessionId()).isEqualTo("s-01");
        assertThat(saved.getValue().getFromSeq()).isEqualTo(10L);
        assertThat(saved.getValue().getToSeq()).isEqualTo(15L);
        assertThat(saved.getValue().getReason()).isEqualTo("queue_overflow");
        assertThat(saved.getValue().getTsMs()).isEqualTo(1_760_000_000_200L);
        assertThat(saved.getValue().getServerReceivedAtMs()).isEqualTo(1_000L);
        assertThat(saved.getValue().getPayload()).isEqualTo(new String(payload, UTF_8));
        assertThat(saved.getValue().getPayloadSha256()).hasSize(64);
    }

    @Test
    @DisplayName("같은 회원과 세션과 시작 순번의 기록이 이미 있으면 저장하지 않고 DUPLICATE를 돌려준다")
    void 같은_구간이_이미_있으면_DUPLICATE를_돌려준다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"queue_overflow\"").getBytes(UTF_8);
        given(sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(MEMBER_ID, "s-01", 10L))
                .willReturn(true);

        // when
        SensorBatchResultResponse response = service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L);

        // then
        assertThat(response.result()).isEqualTo(SensorBatchResult.DUPLICATE);
        then(sensorDroppedRangeRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("동시에 들어온 같은 기록이 유일 키에서 막히고 행이 확인되면 DUPLICATE를 돌려준다")
    void 유일_키_경합이고_행이_있으면_DUPLICATE를_돌려준다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"queue_overflow\"").getBytes(UTF_8);
        given(sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(MEMBER_ID, "s-01", 10L))
                .willReturn(false, true);
        given(sensorDroppedRangeRepository.save(any(SensorDroppedRange.class)))
                .willThrow(new DataIntegrityViolationException("uk_sensor_dropped_range"));

        // when
        SensorBatchResultResponse response = service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L);

        // then
        assertThat(response.result()).isEqualTo(SensorBatchResult.DUPLICATE);
    }

    @Test
    @DisplayName("저장이 무결성 오류로 실패했는데 같은 행이 없으면 예외를 그대로 전달한다")
    void 무결성_오류인데_행이_없으면_예외를_전달한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"queue_overflow\"").getBytes(UTF_8);
        given(sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(MEMBER_ID, "s-01", 10L))
                .willReturn(false, false);
        given(sensorDroppedRangeRepository.save(any(SensorDroppedRange.class)))
                .willThrow(new DataIntegrityViolationException("fk"));

        // when & then
        assertThatThrownBy(() -> service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("끝 순번이 시작 순번보다 작으면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 끝_순번이_시작보다_작으면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = dropped(15, 10, "\"queue_overflow\"").getBytes(UTF_8);

        // when & then
        assertThatThrownBy(() -> service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENSOR_BATCH_INVALID);
        then(sensorDroppedRangeRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("순번에 실수가 오면 잘라 저장하지 않고 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 순번에_실수가_오면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"queue_overflow\"").replace("\"from_seq\":10", "\"from_seq\":10.9")
                .getBytes(UTF_8);

        // when & then
        assertThatThrownBy(() -> service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENSOR_BATCH_INVALID);
        then(sensorDroppedRangeRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결측 사유가 없으면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 결측_사유가_없으면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "null").getBytes(UTF_8);

        // when & then
        assertThatThrownBy(() -> service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENSOR_BATCH_INVALID);
    }

    private SensorDroppedRangeService service() {
        return new SensorDroppedRangeService(sensorDroppedRangeRepository, VALIDATOR, new ObjectMapper());
    }

    /** 앱 develop 17cd0a1 `HeartRatePlugin.sensorItemToJson`이 만드는 형식. */
    private static String dropped(long fromSeq, long toSeq, String reason) {
        return """
            {"stream":"imu_dropped","session_id":"s-01","from_seq":%d,"to_seq":%d,"reason":%s,"ts_ms":1760000000200}"""
                .formatted(fromSeq, toSeq, reason);
    }
}
