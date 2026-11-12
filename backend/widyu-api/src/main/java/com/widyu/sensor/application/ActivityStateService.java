package com.widyu.sensor.application;

import com.widyu.global.properties.SensorProperties;
import com.widyu.location.raw.LocationFix;
import com.widyu.location.raw.repository.LocationFixRepository;
import com.widyu.sensor.ActivityWindow;
import com.widyu.sensor.dto.request.SensorBatchRequest;
import com.widyu.sensor.repository.ActivityWindowRepository;
import com.widyu.sensor.repository.SensorBatchRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 손목 움직임으로 5초 구간의 활동 상태를 정해 저장하고, 심박 측정 시각에 붙일 상태를 찾는다(LLD-0138, ADR-0042 결정 3).
 *
 * <p><b>움직임 크기의 정의는 가정이다.</b> 연구팀 규칙 문서가 오기 전까지 「손목 3축 가속도 벡터 크기에서 1g를 뺀 값의
 * 구간 내 표준편차」로 둔다. 문서가 오면 이 클래스의 계산과 LLD-0138 4절을 함께 바꾼다.
 *
 * <p>상태 저장은 IMU 묶음 저장의 부수 작업이다. 실패해도 예외를 던지지 않고 WARN만 남긴다. 원시 센서값은 로그에 남기지 않는다(정책 1.6.7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityStateService {

    private static final String UNKNOWN = "UNKNOWN";
    private static final String RECOVERY = "RECOVERY";
    private static final String IN_VEHICLE = "IN_VEHICLE";
    private static final String STREAM_WATCH = "imu_watch";
    private static final long ONE_G_MG = 1000L;
    // 워치 가속도계 측정 범위(보통 ±16g)를 넉넉히 넘는 상한. 이상값이 제곱합을 넘치게 하지 않는다.
    private static final long MAX_MAGNITUDE_MG = 64_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final double KMH_PER_MPS = 3.6;

    private final ActivityWindowRepository activityWindowRepository;
    private final SensorBatchRepository sensorBatchRepository;
    private final LocationFixRepository locationFixRepository;
    private final SensorProperties sensorProperties;
    private final PlatformTransactionManager transactionManager;

    /**
     * 저장된 워치 IMU 묶음의 가속도를 구간별로 더한다. 수신 시 파싱한 값을 그대로 쓰고 S3 원문을 다시 읽지 않는다.
     * 같은 {@code batch_id} 재전송은 {@code sensor_batch} UK에서 끝나 여기 오지 않는다.
     */
    public void record(Long memberId, String batchId, String stream, SensorBatchRequest.Clock clock,
                       SensorBatchRequest.Axis acc, SensorBatchRequest.Resend resend) {
        if (!STREAM_WATCH.equals(stream) || acc == null) {
            return;
        }
        try {
            // 새 batch_id로 다시 보낸 묶음은 원 묶음과 샘플이 겹친다. 계보에서 가장 먼저 저장된 묶음만 더한다.
            if (earlierInLineage(memberId, batchId, resend)) {
                return;
            }
            Map<Long, long[]> sums = sumByWindow(clock, acc);
            try {
                write(memberId, sums);
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                // 같은 새 구간을 두 묶음이 동시에 INSERT했다. 다시 하면 잠금 조회로 합친다.
                write(memberId, sums);
            }
        } catch (Exception e) {
            log.warn("활동 상태 저장 실패: memberId={}, errorType={}", memberId, e.getClass().getSimpleName());
        }
    }

    /**
     * 심박 묶음의 측정 시각 범위에 필요한 구간과 위치를 한 번에 읽고, 시각을 상태로 바꾸는 함수를 돌려준다.
     * 값은 {@code REST}, {@code LOW}, {@code ACTIVE}, {@code RECOVERY}, {@code IN_VEHICLE}, {@code UNKNOWN}이다.
     */
    public LongFunction<String> contexts(Long memberId, long firstTsMs, long lastTsMs) {
        SensorProperties.Activity props = sensorProperties.activity();
        long windowMs = props.windowSec() * 1000L;
        long recoveryMs = props.recoverySec() * 1000L;
        long vehicleMs = props.vehicleSec() * 1000L;

        Map<Long, ActivityWindow> windows = new HashMap<>();
        for (ActivityWindow window : activityWindowRepository.findByMemberIdAndWindowStartMsBetween(
                memberId, windowStart(firstTsMs - recoveryMs, windowMs) - windowMs, lastTsMs)) {
            windows.put(window.getWindowStartMs(), window);
        }
        // 차량 판정은 tsMs-vehicleMs 이전의 마지막 위치 하나가 필요해 그만큼 더 앞에서 읽는다.
        List<LocationFix> fixes = locationFixRepository.findByMemberIdAndTsMsBetweenOrderByTsMsAscSeqAsc(
                memberId, firstTsMs - 2 * vehicleMs, lastTsMs).stream()
                .filter(fix -> isUsable(fix, props))
                .toList();

        return tsMs -> contextAt(tsMs, windows, fixes, props);
    }

    private String contextAt(long tsMs, Map<Long, ActivityWindow> windows, List<LocationFix> fixes,
                             SensorProperties.Activity props) {
        if (inVehicle(tsMs, fixes, props)) {
            return IN_VEHICLE;
        }
        long windowMs = props.windowSec() * 1000L;
        ActivityWindow current = windows.get(windowStart(tsMs, windowMs));
        if (current == null || current.getState() == null) {
            return UNKNOWN;
        }
        if (current.getState() == ActivityWindow.State.ACTIVE) {
            return current.getState().name();
        }
        long recoveryMs = props.recoverySec() * 1000L;
        for (ActivityWindow window : windows.values()) {
            boolean activeBefore = window.getState() == ActivityWindow.State.ACTIVE
                    && window.getWindowStartMs() < current.getWindowStartMs();
            if (activeBefore && tsMs - window.getWindowEndMs() <= recoveryMs) {
                return RECOVERY;
            }
        }
        return current.getState().name();
    }

    /**
     * {@code tsMs - vehicleSec} 이전의 마지막 위치와 그 뒤 {@code tsMs}까지의 위치가 모두 기준 속도를 넘으면 차량 이동이다.
     * 위치는 움직임·주기·사건 때만 오므로 두 관측이 없으면 이동 중으로 보지 않는다(ADR-0042 부정 결과).
     * ponytail: 앞쪽 위치는 2×vehicleSec 안에서만 찾는다. 더 성긴 위치 주기가 생기면 조회 범위를 늘린다.
     */
    private boolean inVehicle(long tsMs, List<LocationFix> fixes, SensorProperties.Activity props) {
        long cutoff = tsMs - props.vehicleSec() * 1000L;
        LocationFix anchor = null;
        boolean seenAfter = false;
        for (LocationFix fix : fixes) {
            if (fix.getTsMs() < cutoff - props.vehicleSec() * 1000L || fix.getTsMs() > tsMs) {
                continue;
            }
            if (fix.getTsMs() <= cutoff) {
                anchor = fix;
                continue;
            }
            if (!fast(fix, props)) {
                return false;
            }
            seenAfter = true;
        }
        return anchor != null && seenAfter && fast(anchor, props);
    }

    private boolean fast(LocationFix fix, SensorProperties.Activity props) {
        return fix.getSpeedMps() * KMH_PER_MPS > props.vehicleSpeedKmh();
    }

    /** 정확도와 속도가 있고 기준 오차 안이며 모의 위치가 아닌 점만 쓴다. */
    private boolean isUsable(LocationFix fix, SensorProperties.Activity props) {
        return fix.getAccuracyM() != null && fix.getAccuracyM() <= props.vehicleMaxAccuracyM()
                && fix.getSpeedMps() != null && !Boolean.TRUE.equals(fix.getIsMock());
    }

    /**
     * 계보는 원 묶음과 그 재전송들이다. 묶음은 이 호출 전에 저장돼 PK가 정해졌으므로, 도착 순서나 동시 저장과 무관하게
     * 계보에서 PK가 가장 작은 묶음 하나만 더한다. 원 묶음이 자기 자신이면 재전송이 아닌 것으로 본다.
     * ponytail: 작은 PK 쪽 INSERT가 커밋되기 전 순간에 큰 쪽이 조회하면 둘 다 더한다. 생기면 묶음별 합산 기록을 둔다.
     */
    private boolean earlierInLineage(Long memberId, String batchId, SensorBatchRequest.Resend resend) {
        String rootBatchId = batchId;
        if (resend != null && Boolean.TRUE.equals(resend.isResend()) && resend.originalBatchId() != null) {
            rootBatchId = resend.originalBatchId();
        }
        return sensorBatchRepository.existsEarlierInLineage(memberId, rootBatchId, batchId);
    }

    /** 샘플 배열을 한 번 돈다. 시각은 SensorBatchService와 같은 환산식(LLD-0041 4.3)이다. */
    private Map<Long, long[]> sumByWindow(SensorBatchRequest.Clock clock, SensorBatchRequest.Axis acc) {
        long windowMs = sensorProperties.activity().windowSec() * 1000L;
        long anchorElapsedNs = Long.parseLong(clock.anchorElapsedNs());
        long elapsedNs = Long.parseLong(acc.t0ElapsedNs());
        Map<Long, long[]> sums = new TreeMap<>();
        for (int i = 0; i < acc.n(); i++) {
            if (i > 0) {
                elapsedNs += acc.dtNs().get(i - 1);
            }
            long epochMs = clock.anchorEpochMs() + Math.floorDiv(elapsedNs - anchorElapsedNs, NANOS_PER_MILLI);
            List<Integer> mg = acc.mg().get(i);
            long x = mg.get(0);
            long y = mg.get(1);
            long z = mg.get(2);
            // long 제곱합은 int 축 값 셋이면 넘칠 수 있어 double로 계산하고 상한을 둔다.
            double magnitudeMg = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
            long deviationMg = Math.min(Math.round(magnitudeMg), MAX_MAGNITUDE_MG) - ONE_G_MG;
            long[] sum = sums.computeIfAbsent(windowStart(epochMs, windowMs), key -> new long[3]);
            sum[0]++;
            sum[1] += deviationMg;
            sum[2] += deviationMg * deviationMg;
        }
        return sums;
    }

    private void write(Long memberId, Map<Long, long[]> sums) {
        long windowMs = sensorProperties.activity().windowSec() * 1000L;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Map.Entry<Long, long[]> entry : sums.entrySet()) {
                long start = entry.getKey();
                long[] sum = entry.getValue();
                ActivityWindow window = activityWindowRepository.findForUpdate(memberId, start)
                        .orElseGet(() -> ActivityWindow.empty(memberId, start, start + windowMs));
                long count = window.getSampleCount() + sum[0];
                long sumMg = window.getMagnitudeSumMg() + sum[1];
                long sqSumMg = window.getMagnitudeSqSumMg() + sum[2];
                window.add((int) sum[0], sum[1], sum[2], classify(count, sumMg, sqSumMg));
                activityWindowRepository.save(window);
            }
        });
    }

    /** 경계값은 아래쪽 상태에서 뺀다. 0.04g는 LOW, 0.20g는 ACTIVE다. */
    private ActivityWindow.State classify(long count, long sumMg, long sqSumMg) {
        SensorProperties.Activity props = sensorProperties.activity();
        if (count < props.minWindowSamples()) {
            return null;
        }
        double stdG = ActivityWindow.magnitudeStdG(count, sumMg, sqSumMg);
        if (stdG >= props.activeMinG()) {
            return ActivityWindow.State.ACTIVE;
        }
        if (stdG >= props.restMaxG()) {
            return ActivityWindow.State.LOW;
        }
        return ActivityWindow.State.REST;
    }

    private static long windowStart(long epochMs, long windowMs) {
        return Math.floorDiv(epochMs, windowMs) * windowMs;
    }
}
