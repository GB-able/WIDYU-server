package com.widyu.sensor.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.ErrorCode;
import com.widyu.sensor.SensorIngestChannel;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 서비스 밖에서 거절한 WebSocket 센서 묶음(본문 크기 초과, 센서 실행기 포화)을 거절 기록에 남긴다(LLD-0166 5.2).
 *
 * <p>거절은 STOMP 수신 스레드에서 판단한다. 거기서 DB에 쓰면 커넥션을 기다리는 동안(최대 3초) 위치, 심박 수신까지 멈추고,
 * 포화는 대개 S3나 DB가 느릴 때 생겨 바로 그 순간 DB 대기를 더한다. 그래서 수신 스레드는 넘기기만 하고, 원문 해석과
 * 저장은 전용 스레드 하나가 한다. 대기열이 차면 기록을 버리고 센다. 원문은 저장하지 않는다.
 */
@Slf4j
@Component
public class SensorRejectionAsyncRecorder {

    static final String DROPPED_METRIC = "sensor.batch.rejection.record.dropped";
    /** 64칸 × WS 본문 상한 64KB = 4MB. */
    private static final int QUEUE_CAPACITY = 64;
    private static final long SHUTDOWN_WAIT_SECONDS = 5L;

    private final SensorBatchRejectionRecorder recorder;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Executor executor;

    @Autowired
    public SensorRejectionAsyncRecorder(
            SensorBatchRejectionRecorder recorder, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        // ponytail: 이 기록 전용 스레드 1개 + 64칸. 거절이 몰려도 DB 커넥션 1개, 메모리 4MB를 넘지 않는다.
        // 버려지는 기록(DROPPED_METRIC)이 잦으면 칸 수를 설정으로 뺀다.
        this(recorder, objectMapper, meterRegistry, new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                Thread.ofPlatform().daemon().name("sensorRejectionRecord-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()));
    }

    SensorRejectionAsyncRecorder(SensorBatchRejectionRecorder recorder, ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry, Executor executor) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
    }

    /** 수신 스레드에서 부른다. 막히지 않고 바로 돌아온다. */
    public void submit(Long memberId, byte[] payload, ErrorCode errorCode, long receivedAtMs) {
        try {
            executor.execute(() -> record(memberId, payload, errorCode, receivedAtMs));
        } catch (RejectedExecutionException e) {
            meterRegistry.counter(DROPPED_METRIC).increment();
        }
    }

    private void record(Long memberId, byte[] payload, ErrorCode errorCode, long receivedAtMs) {
        try {
            recorder.record(SensorBatchService.rejectionOf(
                    memberId, readTree(payload), errorCode.getCode(), SensorIngestChannel.WS, receivedAtMs));
        } catch (RuntimeException e) {
            log.warn("센서 배치 거절 기록 실패: memberId={}, code={}, errorType={}",
                    memberId, errorCode.getCode(), e.getClass().getSimpleName());
        }
    }

    /** 식별자만 얕게 읽는다. 못 읽으면 식별자 없이 남긴다. 예외 메시지에 샘플 값이 섞일 수 있어 남기지 않는다. */
    private JsonNode readTree(byte[] payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException e) {
            return null;
        }
    }

    @PreDestroy
    void close() throws InterruptedException {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
            service.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }
}
