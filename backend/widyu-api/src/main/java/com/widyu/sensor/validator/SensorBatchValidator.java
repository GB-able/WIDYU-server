package com.widyu.sensor.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.sensor.dto.request.HeartRateBatchRequest;
import com.widyu.sensor.dto.request.SensorBatchRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * v2 센서 배치(IMU·심박) 본문 검증(LLD-0041, LLD-0047). 형식이 틀리면 {@code SENSOR_BATCH_INVALID},
 * 샘플이 틀리면 {@code SENSOR_SAMPLE_INVALID}로 거절한다.
 * 원시 센서값은 어떤 로그 레벨에도 남기지 않는다(정책 1.6.7).
 */
public final class SensorBatchValidator {

    private static final String STREAM_WATCH = "imu_watch";
    private static final int MAX_HR_SAMPLES = 60;
    private static final int MAX_BPM = 300;
    private static final String ACCURACY_UNRELIABLE = "UNRELIABLE";
    private static final Set<String> ACCURACY_VALUES =
            Set.of("HIGH", "MEDIUM", "LOW", "UNRELIABLE", "UNKNOWN");
    private static final String SOURCE_WATCH = "watch";
    private static final String SOURCE_PHONE = "phone";
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_SAMPLES = 1000;
    private static final int AXIS_COLUMNS = 3;
    private static final long MAX_DT_NS = 4_294_967_295L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int MAX_TRIGGER_KIND_LENGTH = 20;
    private static final int MAX_BACKFILL_FOR_LENGTH = 255;
    private static final int MAX_RUN_ID_LENGTH = 64;
    public static final Pattern BATCH_ID_PATTERN = Pattern.compile("^[a-z0-9]{26}$");
    public static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[a-z0-9._-]{1,64}$");

    private final Validator validator;

    public SensorBatchValidator(Validator validator) {
        this.validator = validator;
    }

    public ValidatedBatch validate(SensorBatchRequest request, JsonNode root) {
        Set<ConstraintViolation<SensorBatchRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        if (request.v() != FORMAT_VERSION) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        if (!sourceMatchesStream(request)) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        validateAxisPresence(request);
        validateResend(request, root.path("resend"));
        validateTrigger(request.trigger());
        String backfillFor = validateBackfillFor(request.backfillFor());
        if (Boolean.TRUE.equals(request.gyroBackfill()) && backfillFor == null) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }

        parseElapsedNs(request.clock().anchorElapsedNs());
        if (request.acc() != null) {
            validateAxis(request.acc(), request.acc().mg());
        }
        if (request.gyro() != null) {
            validateAxis(request.gyro(), request.gyro().mrads());
        }
        if (request.trigger() != null && request.trigger().eventElapsedNs() != null) {
            parseElapsedNs(request.trigger().eventElapsedNs());
        }
        return new ValidatedBatch(measuredTimeRange(request), backfillFor);
    }

    private boolean sourceMatchesStream(SensorBatchRequest request) {
        if (STREAM_WATCH.equals(request.stream())) {
            return SOURCE_WATCH.equals(request.source());
        }
        return SOURCE_PHONE.equals(request.source());
    }

    /** 보강 배치는 자이로만 싣고 원 배치를 가리켜야 한다. 그 밖의 배치는 가속도가 필수다. */
    private void validateAxisPresence(SensorBatchRequest request) {
        if (Boolean.TRUE.equals(request.gyroBackfill())) {
            if (request.acc() != null || request.gyro() == null || request.backfillFor() == null) {
                throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
            }
            return;
        }
        if (request.acc() == null) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
    }

    /** 재전송이면 계보 6필드를 갖춰야 한다. {@code original_run_id}는 회차가 없던 배치를 위해 null을 허용한다. */
    private void validateResend(SensorBatchRequest request, JsonNode resendNode) {
        SensorBatchRequest.Resend resend = request.resend();
        if (resend == null) {
            return;
        }
        if (doesNotMatchWhenPresent(BATCH_ID_PATTERN, resend.originalBatchId())
                || isNegative(resend.originalSeq())
                || doesNotMatchWhenPresent(SESSION_ID_PATTERN, resend.originalSessionId())
                || isNegative(resend.resentAtMs())
                || exceedsLength(resend.originalRunId(), MAX_RUN_ID_LENGTH)) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        if (!Boolean.TRUE.equals(resend.isResend())) {
            return;
        }
        if (!hasLineageFields(resendNode)
                || resend.originalBatchId() == null
                || resend.originalSeq() == null
                || resend.originalSessionId() == null
                || resend.resentAtMs() == null
                || !Objects.equals(request.runId(), resend.originalRunId())) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
    }

    private boolean hasLineageFields(JsonNode resendNode) {
        return resendNode.isObject()
                && resendNode.has("is_resend")
                && resendNode.has("original_batch_id")
                && resendNode.has("original_seq")
                && resendNode.has("original_run_id")
                && resendNode.has("original_session_id")
                && resendNode.has("resent_at_ms");
    }

    private void validateTrigger(SensorBatchRequest.Trigger trigger) {
        if (trigger != null && exceedsLength(trigger.kind(), MAX_TRIGGER_KIND_LENGTH)) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
    }

    private String validateBackfillFor(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        List<String> originalBatchIds = new ArrayList<>();
        if (node.isTextual()) {
            originalBatchIds.add(node.textValue());
        } else if (node.isArray() && !node.isEmpty()) {
            for (JsonNode element : node) {
                if (!element.isTextual()) {
                    throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
                }
                originalBatchIds.add(element.textValue());
            }
        } else {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        if (originalBatchIds.stream().anyMatch(id -> !matches(BATCH_ID_PATTERN, id))) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        String joined = String.join(",", originalBatchIds);
        if (joined.length() > MAX_BACKFILL_FOR_LENGTH) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        return joined;
    }

    private boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private boolean doesNotMatchWhenPresent(Pattern pattern, String value) {
        return value != null && !matches(pattern, value);
    }

    private boolean isNegative(Long value) {
        return value != null && value < 0;
    }

    private boolean exceedsLength(String value, int maximum) {
        return value != null && value.length() > maximum;
    }

    private void validateAxis(SensorBatchRequest.Axis axis, List<List<Integer>> values) {
        if (axis.n() == null || axis.n() < 1 || axis.n() > MAX_SAMPLES) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        if (axis.fsHzRequested() == null || axis.fsHzRequested() <= 0) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        // dt_ns는 길이 n-1이고 0이나 uint32 초과가 없어야 한다(검사기 A1·A9).
        if (axis.dtNs() == null || axis.dtNs().size() != axis.n() - 1) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        for (Long interval : axis.dtNs()) {
            if (interval == null || interval <= 0 || interval > MAX_DT_NS) {
                throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
            }
        }
        // 값 배열은 정확히 n행 3열이어야 한다. A1이 dt 길이만 보므로 여기서 따로 본다(검사기 A13).
        if (values == null || values.size() != axis.n()) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        for (List<Integer> row : values) {
            if (row == null || row.size() != AXIS_COLUMNS || row.contains(null)) {
                throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
            }
        }
        parseElapsedNs(axis.t0ElapsedNs());
    }

    /** 10진 정수 문자열을 long으로. 형식이 아니거나 long 범위를 넘으면 거부한다(검사기 A3). */
    public static long parseElapsedNs(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        BigInteger parsed;
        try {
            parsed = new BigInteger(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        if (parsed.signum() < 0 || parsed.compareTo(MAX_LONG) > 0) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        return parsed.longValue();
    }

    /** 이 배치가 덮는 축 시각 범위(부팅 기준 ns). 매핑의 관측 범위 근거다. */
    public record ElapsedRange(long minNs, long maxNs) {}

    public static ElapsedRange elapsedRange(SensorBatchRequest request) {
        long minNs = Long.MAX_VALUE;
        long maxNs = Long.MIN_VALUE;
        for (SensorBatchRequest.Axis axis : presentAxes(request)) {
            long t0ElapsedNs = parseElapsedNs(axis.t0ElapsedNs());
            minNs = Math.min(minNs, t0ElapsedNs);
            try {
                maxNs = Math.max(maxNs, Math.addExact(t0ElapsedNs, sumIntervals(axis)));
            } catch (ArithmeticException e) {
                throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
            }
        }
        return new ElapsedRange(minNs, maxNs);
    }

    // ── 심박 배치(LLD-0047) ──────────────────────────────────────────────

    public void validateHeartRate(HeartRateBatchRequest request) {
        Set<ConstraintViolation<HeartRateBatchRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        if (request.v() != FORMAT_VERSION) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        // 계약에서 삭제된 필드다(검사기 E6). 남아 있으면 앱이 옛 형식으로 보내고 있다는 뜻이다.
        if (isPresent(request.location()) || isPresent(request.context())) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
        validateHeartRateResend(request.runId(), request.resend());
        parseElapsedNs(request.clock().anchorElapsedNs());
        validateHeartRateSamples(request.samples());
    }

    private void validateHeartRateSamples(List<HeartRateBatchRequest.Sample> samples) {
        if (samples.isEmpty() || samples.size() > MAX_HR_SAMPLES) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        long previousTsMs = Long.MIN_VALUE;
        for (HeartRateBatchRequest.Sample sample : samples) {
            if (sample.bpm() == null || sample.bpm() < 0 || sample.bpm() > MAX_BPM) {
                throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
            }
            // 배치 안에서 엄격 증가여야 한다. 역행·중복 금지(검사기 E2).
            if (sample.tsMs() == null || sample.tsMs() <= previousTsMs) {
                throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
            }
            if (sample.accuracy() == null || !ACCURACY_VALUES.contains(sample.accuracy())) {
                throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
            }
            // bpm 0은 신뢰할 수 없는 샘플일 때만 허용한다(검사기 E7).
            if (sample.bpm() == 0 && !ACCURACY_UNRELIABLE.equals(sample.accuracy())) {
                throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
            }
            previousTsMs = sample.tsMs();
        }
    }

    private boolean isPresent(JsonNode node) {
        return node != null && !node.isNull();
    }

    private void validateHeartRateResend(String runId, SensorBatchRequest.Resend resend) {
        if (resend == null) {
            return;
        }
        if (doesNotMatchWhenPresent(BATCH_ID_PATTERN, resend.originalBatchId())
                || isNegative(resend.originalSeq())
                || doesNotMatchWhenPresent(SESSION_ID_PATTERN, resend.originalSessionId())
                || isNegative(resend.resentAtMs())
                || exceedsLength(resend.originalRunId(), MAX_RUN_ID_LENGTH)
                || (Boolean.TRUE.equals(resend.isResend())
                && (resend.originalBatchId() == null || resend.originalSeq() == null
                || resend.originalSessionId() == null || resend.resentAtMs() == null
                || !Objects.equals(runId, resend.originalRunId())))) {
            throw new BusinessException(ErrorCode.SENSOR_BATCH_INVALID);
        }
    }

    /** 4.3 환산식. 원값이 컬럼에 남으므로 기준점이 바뀌면 다시 환산할 수 있다. */
    private static long epochMs(long elapsedNs, SensorBatchRequest.Clock clock, long anchorElapsedNs) {
        try {
            long elapsedDelta = Math.subtractExact(elapsedNs, anchorElapsedNs);
            long elapsedDeltaMs = Math.floorDiv(elapsedDelta, NANOS_PER_MILLI);
            return Math.addExact(clock.anchorEpochMs(), elapsedDeltaMs);
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
    }

    private static long sumIntervals(SensorBatchRequest.Axis axis) {
        long total = 0L;
        for (Long interval : axis.dtNs()) {
            total = Math.addExact(total, interval);
        }
        return total;
    }

    private static List<SensorBatchRequest.Axis> presentAxes(SensorBatchRequest request) {
        List<SensorBatchRequest.Axis> axes = new ArrayList<>();
        if (request.acc() != null) {
            axes.add(request.acc());
        }
        if (request.gyro() != null) {
            axes.add(request.gyro());
        }
        return axes;
    }

    private static TimeRange measuredTimeRange(SensorBatchRequest request) {
        SensorBatchRequest.Clock clock = request.clock();
        long anchorElapsedNs = parseElapsedNs(clock.anchorElapsedNs());
        long startMs = Long.MAX_VALUE;
        long endMs = Long.MIN_VALUE;
        try {
            for (SensorBatchRequest.Axis axis : presentAxes(request)) {
                long t0ElapsedNs = parseElapsedNs(axis.t0ElapsedNs());
                long lastElapsedNs = Math.addExact(t0ElapsedNs, sumIntervals(axis));
                startMs = Math.min(startMs, epochMs(t0ElapsedNs, clock, anchorElapsedNs));
                endMs = Math.max(endMs, epochMs(lastElapsedNs, clock, anchorElapsedNs));
            }
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.SENSOR_SAMPLE_INVALID);
        }
        return new TimeRange(startMs, endMs);
    }

    public record ValidatedBatch(TimeRange measuredTimeRange, String backfillFor) {}

    public record TimeRange(long startMs, long endMs) {}
}
