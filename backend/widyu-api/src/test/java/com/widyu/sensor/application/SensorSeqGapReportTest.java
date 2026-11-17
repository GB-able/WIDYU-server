package com.widyu.sensor.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.SensorBatchRejection;
import com.widyu.sensor.SensorDroppedRange;
import com.widyu.sensor.SensorIngestChannel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SensorSeqGapReport 단위 테스트")
class SensorSeqGapReportTest {

    private static final long T0 = 1_793_319_000_000L;

    @Test
    @DisplayName("빠진 번호 구간에 기기 버림 기록과 서버 거절 기록이 일부만 있으면 둘 다와 기록 없음을 함께 원인으로 적는다")
    void 일부만_기록이_있으면_원인을_함께_적는다() {
        // given: 1번과 6번만 왔다. 2~3번은 버림 기록, 4번은 거절 기록, 5번은 기록 없음
        List<SensorBatch> batches = List.of(
                batch("imu_watch", "s-01", 1L, SensorIngestChannel.WS),
                batch("imu_watch", "s-01", 6L, SensorIngestChannel.REST));
        List<SensorDroppedRange> dropped = List.of(dropped("s-01", "imu_watch", 2L, 3L, "queue_overflow"));
        List<SensorBatchRejection> rejections = List.of(rejection("s-01", "imu_watch", 4L, "SRV_5031"));

        // when
        SensorSeqGapReport.Lines lines = SensorSeqGapReport.build(batches, dropped, rejections);

        // then
        assertThat(lines.gaps()).containsExactly(SensorWindowCsv.line("imu_watch", "gw-1", "s-01", 2, 5, 4,
                T0 + 1_000 + 1_000, SensorWindowCsv.kst(T0 + 2_000), T0 + 6_000, SensorWindowCsv.kst(T0 + 6_000),
                2, "queue_overflow", 1, "SRV_5031", "WS", 1, "DEVICE_DROPPED;SERVER_REJECTED;NO_RECORD"));
        assertThat(lines.sessions()).containsExactly(
                SensorWindowCsv.line("imu_watch", "gw-1", "s-01", 1, 6, 6, 2, 4, 0.6667, 1, 1, 1));
    }

    @Test
    @DisplayName("재전송 묶음이 원 세션의 원 번호를 채우면 그 번호는 빠진 것으로 적지 않는다")
    void 재전송으로_채운_번호는_빠진_것으로_적지_않는다() {
        // given: 원 세션 s-01의 2번을 새 세션 s-02의 묶음으로 다시 보냈다
        SensorBatch resent = SensorBatch.builder()
                .batchId("r%025d".formatted(0))
                .member(Member.createMember(MemberType.SENIOR, "시니어", "01012345678"))
                .stream("hr")
                .deviceId("gw-1")
                .sessionId("s-02")
                .seq(0L)
                .measuredAtStartMs(T0 + 2_000)
                .measuredAtEndMs(T0 + 3_000)
                .isResend(true)
                .originalSessionId("s-01")
                .originalSeq(2L)
                .ingestChannel(SensorIngestChannel.REST)
                .build();
        List<SensorBatch> batches = List.of(
                batch("hr", "s-01", 1L, SensorIngestChannel.WS),
                batch("hr", "s-01", 3L, SensorIngestChannel.WS),
                resent);

        // when
        SensorSeqGapReport.Lines lines = SensorSeqGapReport.build(batches, List.of(), List.of());

        // then
        assertThat(lines.gaps()).isEmpty();
        assertThat(lines.sessions()).containsExactly(
                SensorWindowCsv.line("hr", "gw-1", "s-01", 1, 3, 3, 3, 0, 0.0, 0, 2, 0),
                SensorWindowCsv.line("hr", "gw-1", "s-02", 0, 0, 1, 1, 0, 0.0, 0, 0, 1));
    }

    @Test
    @DisplayName("세션의 처음과 끝 번호 밖에서 버린 기록은 빠진 구간으로 적지 않는다")
    void 세션_경계_밖은_빠진_구간으로_적지_않는다() {
        // given: 10~12번이 왔고, 버림 기록은 세션 앞 0~9번과 뒤 13~20번이다
        List<SensorBatch> batches = List.of(
                batch("imu_watch", "s-01", 10L, SensorIngestChannel.WS),
                batch("imu_watch", "s-01", 11L, SensorIngestChannel.WS),
                batch("imu_watch", "s-01", 12L, SensorIngestChannel.WS));
        List<SensorDroppedRange> dropped = List.of(
                dropped("s-01", "imu_watch", 0L, 9L, "queue_overflow"),
                dropped("s-01", "imu_watch", 13L, 20L, "queue_overflow"));

        // when
        SensorSeqGapReport.Lines lines = SensorSeqGapReport.build(batches, dropped, List.of());

        // then
        assertThat(lines.gaps()).isEmpty();
        assertThat(lines.sessions()).containsExactly(
                SensorWindowCsv.line("imu_watch", "gw-1", "s-01", 10, 12, 3, 3, 0, 0.0, 0, 3, 0));
    }

    @Test
    @DisplayName("다른 세션이나 다른 자료 종류의 버림, 거절 기록은 대조하지 않고 기록 없음으로 적는다")
    void 다른_세션이나_자료_종류의_기록은_대조하지_않는다() {
        // given
        List<SensorBatch> batches = List.of(
                batch("hr", "s-01", 1L, SensorIngestChannel.WS),
                batch("hr", "s-01", 3L, SensorIngestChannel.WS));
        List<SensorDroppedRange> dropped = List.of(
                dropped("s-01", "imu_watch", 2L, 2L, "queue_overflow"),
                dropped("s-09", "hr", 2L, 2L, "queue_overflow"));
        List<SensorBatchRejection> rejections = List.of(rejection("s-01", "imu_watch", 2L, "SENSOR_4003"));

        // when
        SensorSeqGapReport.Lines lines = SensorSeqGapReport.build(batches, dropped, rejections);

        // then
        assertThat(lines.gaps()).hasSize(1);
        assertThat(lines.gaps().get(0)).endsWith(",0,,0,,,1,NO_RECORD\n");
    }

    private static SensorBatch batch(String stream, String sessionId, long seq, SensorIngestChannel channel) {
        long start = T0 + seq * 1_000;
        return SensorBatch.builder()
                .batchId("b%025d".formatted(seq))
                .member(Member.createMember(MemberType.SENIOR, "시니어", "01012345678"))
                .stream(stream)
                .deviceId("gw-1")
                .sessionId(sessionId)
                .seq(seq)
                .measuredAtStartMs(start)
                .measuredAtEndMs(start + 1_000)
                .isResend(false)
                .ingestChannel(channel)
                .build();
    }

    private static SensorDroppedRange dropped(String sessionId, String stream, long from, long to, String reason) {
        return SensorDroppedRange.builder().memberId(28L).sessionId(sessionId).droppedStream(stream)
                .fromSeq(from).toSeq(to).reason(reason).tsMs(T0 + 1_800_000).serverReceivedAtMs(T0 + 1_801_000)
                .build();
    }

    private static SensorBatchRejection rejection(String sessionId, String stream, long seq, String errorCode) {
        return SensorBatchRejection.builder().memberId(28L).stream(stream).sessionId(sessionId).seq(seq)
                .errorCode(errorCode).ingestChannel(SensorIngestChannel.WS).receivedAtMs(T0 + 4_500).build();
    }
}
