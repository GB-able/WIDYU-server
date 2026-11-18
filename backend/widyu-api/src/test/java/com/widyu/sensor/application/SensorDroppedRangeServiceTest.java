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
import java.security.MessageDigest;
import java.util.HexFormat;
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
    @DisplayName("옛 형식 imu_dropped 기록을 받으면 데이터 종류를 imu_watch로 채워 원문과 함께 저장하고 STORED를 돌려준다")
    void 옛_형식_버린_구간_기록을_받으면_imu_watch로_원문과_함께_저장한다() throws Exception {
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
        assertThat(saved.getValue().getDroppedStream()).isEqualTo("imu_watch");
        assertThat(saved.getValue().getFromSeq()).isEqualTo(10L);
        assertThat(saved.getValue().getToSeq()).isEqualTo(15L);
        assertThat(saved.getValue().getReason()).isEqualTo("queue_overflow");
        assertThat(saved.getValue().getTsMs()).isEqualTo(1_760_000_000_200L);
        assertThat(saved.getValue().getServerReceivedAtMs()).isEqualTo(1_000L);
        assertThat(saved.getValue().getPayload()).isEqualTo(new String(payload, UTF_8));
        assertThat(saved.getValue().getPayloadSha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));
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

    @Test
    @DisplayName("문자열 필드에 숫자나 불리언이 오면 바꿔 저장하지 않고 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 문자열_필드에_다른_타입이_오면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "true").getBytes(UTF_8);

        // when & then
        assertInvalid(payload);
    }

    @Test
    @DisplayName("세션 ID 형식이 맞지 않으면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 세션_ID_형식이_맞지_않으면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"queue_overflow\"").replace("s-01", "BAD SESSION").getBytes(UTF_8);

        // when & then
        assertInvalid(payload);
    }

    @Test
    @DisplayName("시작 순번이 음수면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 시작_순번이_음수면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = dropped(-1, 15, "\"queue_overflow\"").getBytes(UTF_8);

        // when & then
        assertInvalid(payload);
    }

    @Test
    @DisplayName("결측 사유가 64자를 넘으면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 결측_사유가_64자를_넘으면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"" + "r".repeat(65) + "\"").getBytes(UTF_8);

        // when & then
        assertInvalid(payload);
    }

    @Test
    @DisplayName("새 형식 dropped 기록을 받으면 보낸 데이터 종류와 사유로 저장하고 STORED를 돌려준다")
    void 새_형식_버린_구간_기록을_받으면_데이터_종류와_사유로_저장한다() throws Exception {
        // given
        byte[] payload = generic("\"hr\"", "\"enqueue_failed\"").getBytes(UTF_8);
        given(sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(MEMBER_ID, "hr-01", 7L))
                .willReturn(false);

        // when
        SensorBatchResultResponse response = service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L);

        // then
        assertThat(response.result()).isEqualTo(SensorBatchResult.STORED);
        ArgumentCaptor<SensorDroppedRange> saved = ArgumentCaptor.forClass(SensorDroppedRange.class);
        then(sensorDroppedRangeRepository).should().save(saved.capture());
        assertThat(saved.getValue().getDroppedStream()).isEqualTo("hr");
        assertThat(saved.getValue().getReason()).isEqualTo("enqueue_failed");
        assertThat(saved.getValue().getFromSeq()).isEqualTo(7L);
        assertThat(saved.getValue().getToSeq()).isEqualTo(7L);
        assertThat(saved.getValue().getPayload()).isEqualTo(new String(payload, UTF_8));
    }

    @Test
    @DisplayName("목록에 없는 사유와 데이터 종류도 거절하지 않고 그대로 저장한다")
    void 목록에_없는_사유와_데이터_종류도_그대로_저장한다() throws Exception {
        // given
        byte[] payload = generic("\"imu_phone\"", "\"battery_saver\"").getBytes(UTF_8);
        given(sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(MEMBER_ID, "hr-01", 7L))
                .willReturn(false);

        // when
        service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L);

        // then
        ArgumentCaptor<SensorDroppedRange> saved = ArgumentCaptor.forClass(SensorDroppedRange.class);
        then(sensorDroppedRangeRepository).should().save(saved.capture());
        assertThat(saved.getValue().getDroppedStream()).isEqualTo("imu_phone");
        assertThat(saved.getValue().getReason()).isEqualTo("battery_saver");
    }

    @Test
    @DisplayName("옛 형식에 타입이 다른 데이터 종류가 섞여 있어도 쓰지 않으므로 imu_watch로 저장한다")
    void 옛_형식에_타입이_다른_데이터_종류가_섞여_있어도_imu_watch로_저장한다() throws Exception {
        // given
        byte[] payload = dropped(10, 15, "\"queue_overflow\"")
                .replace("\"stream\":\"imu_dropped\"", "\"stream\":\"imu_dropped\",\"dropped_stream\":42")
                .getBytes(UTF_8);
        given(sensorDroppedRangeRepository.existsByMemberIdAndSessionIdAndFromSeq(MEMBER_ID, "s-01", 10L))
                .willReturn(false);

        // when
        SensorBatchResultResponse response = service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L);

        // then
        assertThat(response.result()).isEqualTo(SensorBatchResult.STORED);
        ArgumentCaptor<SensorDroppedRange> saved = ArgumentCaptor.forClass(SensorDroppedRange.class);
        then(sensorDroppedRangeRepository).should().save(saved.capture());
        assertThat(saved.getValue().getDroppedStream()).isEqualTo("imu_watch");
    }

    @Test
    @DisplayName("새 형식의 데이터 종류가 문자열이 아니면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 새_형식의_데이터_종류가_문자열이_아니면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = generic("42", "\"queue_overflow\"").getBytes(UTF_8);

        // when & then
        assertInvalid(payload);
    }

    @Test
    @DisplayName("새 형식에 데이터 종류가 없으면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 새_형식에_데이터_종류가_없으면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = generic("null", "\"queue_overflow\"").getBytes(UTF_8);

        // when & then
        assertInvalid(payload);
    }

    @Test
    @DisplayName("새 형식의 데이터 종류 형식이 맞지 않으면 SENSOR_BATCH_INVALID 예외가 발생한다")
    void 새_형식의_데이터_종류_형식이_맞지_않으면_예외가_발생한다() throws Exception {
        // given
        byte[] payload = generic("\"HR\\nINFO\"", "\"queue_overflow\"").getBytes(UTF_8);

        // when & then
        assertInvalid(payload);
    }

    private void assertInvalid(byte[] payload) throws Exception {
        assertThatThrownBy(() -> service().record(MEMBER_ID, payload, JSON.readTree(payload), 1_000L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENSOR_BATCH_INVALID);
        then(sensorDroppedRangeRepository).should(never()).save(any());
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

    /** 새 형식(LLD-0166 3.1). 대기열에 넣지 못한 심박 묶음 하나다. */
    private static String generic(String droppedStream, String reason) {
        return """
            {"stream":"dropped","dropped_stream":%s,"session_id":"hr-01","from_seq":7,"to_seq":7,"reason":%s,"ts_ms":1760000000200}"""
                .formatted(droppedStream, reason);
    }
}
