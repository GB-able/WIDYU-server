package com.widyu.sensor.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.ErrorCode;
import com.widyu.sensor.SensorBatchRejection;
import com.widyu.sensor.SensorIngestChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SensorRejectionAsyncRecorder 단위 테스트")
class SensorRejectionAsyncRecorderTest {

    private static final long RECEIVED_AT_MS = 1_760_000_000_000L;
    private static final String BATCH_JSON = """
            {"v":2,"stream":"imu_watch","batch_id":"01j8zk3v9x2q4m7n8p1r5s6t7u","device_id":"gw-1",
             "session_id":"s-01","seq":88213,"acc":{"mg":[[1,2,3]]}}""";

    @Mock private SensorBatchRejectionRecorder recorder;
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("실행기 포화로 거절한 묶음을 넘기면 원문에서 읽은 식별자와 오류 코드, WS 경로로 거절 기록을 남긴다")
    void 실행기_포화로_거절한_묶음을_넘기면_식별자와_오류_코드로_거절_기록을_남긴다() {
        // given
        SensorRejectionAsyncRecorder asyncRecorder = asyncRecorder(Runnable::run);

        // when
        asyncRecorder.submit(42L, BATCH_JSON.getBytes(UTF_8), ErrorCode.TEMPORARILY_BUSY, RECEIVED_AT_MS);

        // then
        ArgumentCaptor<SensorBatchRejection> saved = ArgumentCaptor.forClass(SensorBatchRejection.class);
        then(recorder).should().record(saved.capture());
        SensorBatchRejection rejection = saved.getValue();
        assertThat(rejection.getMemberId()).isEqualTo(42L);
        assertThat(rejection.getStream()).isEqualTo("imu_watch");
        assertThat(rejection.getBatchId()).isEqualTo("01j8zk3v9x2q4m7n8p1r5s6t7u");
        assertThat(rejection.getDeviceId()).isEqualTo("gw-1");
        assertThat(rejection.getSessionId()).isEqualTo("s-01");
        assertThat(rejection.getSeq()).isEqualTo(88213L);
        assertThat(rejection.getErrorCode()).isEqualTo("SRV_5031");
        assertThat(rejection.getIngestChannel()).isEqualTo(SensorIngestChannel.WS);
        assertThat(rejection.getReceivedAtMs()).isEqualTo(RECEIVED_AT_MS);
    }

    @Test
    @DisplayName("본문을 읽을 수 없는 묶음을 넘기면 식별자 없이 오류 코드만 담은 거절 기록을 남긴다")
    void 본문을_읽을_수_없으면_식별자_없이_거절_기록을_남긴다() {
        // given
        SensorRejectionAsyncRecorder asyncRecorder = asyncRecorder(Runnable::run);

        // when
        asyncRecorder.submit(42L, "{not json".getBytes(UTF_8), ErrorCode.SENSOR_BATCH_TOO_LARGE, RECEIVED_AT_MS);

        // then
        ArgumentCaptor<SensorBatchRejection> saved = ArgumentCaptor.forClass(SensorBatchRejection.class);
        then(recorder).should().record(saved.capture());
        assertThat(saved.getValue().getErrorCode()).isEqualTo("SENSOR_4000");
        assertThat(saved.getValue().getBatchId()).isNull();
        assertThat(saved.getValue().getSeq()).isNull();
        assertThat(saved.getValue().getStream()).isNull();
    }

    @Test
    @DisplayName("기록 대기열이 가득 차면 기록을 버리고 버린 수를 센다")
    void 기록_대기열이_가득_차면_기록을_버리고_센다() {
        // given
        Executor full = task -> {
            throw new RejectedExecutionException("full");
        };
        SensorRejectionAsyncRecorder asyncRecorder = asyncRecorder(full);

        // when
        asyncRecorder.submit(42L, BATCH_JSON.getBytes(UTF_8), ErrorCode.TEMPORARILY_BUSY, RECEIVED_AT_MS);

        // then
        then(recorder).should(never()).record(any());
        assertThat(meterRegistry.counter(SensorRejectionAsyncRecorder.DROPPED_METRIC).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("거절 기록 저장이 실패해도 예외를 던지지 않는다")
    void 거절_기록_저장이_실패해도_예외를_던지지_않는다() {
        // given
        willThrow(new DataAccessResourceFailureException("db down")).given(recorder).record(any());
        SensorRejectionAsyncRecorder asyncRecorder = asyncRecorder(Runnable::run);

        // when / then
        assertThatCode(() -> asyncRecorder.submit(
                42L, BATCH_JSON.getBytes(UTF_8), ErrorCode.TEMPORARILY_BUSY, RECEIVED_AT_MS))
                .doesNotThrowAnyException();
    }

    private SensorRejectionAsyncRecorder asyncRecorder(Executor executor) {
        return new SensorRejectionAsyncRecorder(recorder, new ObjectMapper(), meterRegistry, executor);
    }
}
