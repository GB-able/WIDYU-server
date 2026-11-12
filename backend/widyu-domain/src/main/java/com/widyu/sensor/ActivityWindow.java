package com.widyu.sensor;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 손목 움직임으로 정한 5초 구간 하나의 활동 상태(LLD-0138, ADR-0042 결정 3).
 *
 * <p>워치 IMU 묶음은 1초 단위라 한 구간에 묶음 다섯 개쯤이 나눠 들어온다. 그래서 상태만 두지 않고
 * 움직임 크기의 <b>정수 합계</b>(샘플 수, 합, 제곱합)를 쌓고 상태는 그 합계로 다시 정한다. 정수 덧셈은 순서와
 * 무관하므로 늦게 온 묶음, 역순 도착에도 마지막 행이 같다. 멱등 키는 {@code (member_id, window_start_ms)}다.
 *
 * <p>값은 1mg 단위 정수다. 손목 3축 가속도 벡터 크기를 1mg로 반올림하고 1g(1000mg)를 뺀 값을 더한다.
 */
@Entity
@Getter
@Table(
    name = "activity_window",
    uniqueConstraints = @UniqueConstraint(
            name = "uk_activity_window_member_start", columnNames = {"member_id", "window_start_ms"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityWindow extends BaseTimeEntity {

    /** 구간에 저장하는 움직임 상태. 회복 중·차량 이동은 심박에 붙일 때 계산하므로 여기 없다. */
    public enum State { REST, LOW, ACTIVE }

    private static final double MG_PER_G = 1000.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_window_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 구간 시작(epoch ms, 구간 길이의 배수). 기기 측정 시각 기준이다. */
    @Column(name = "window_start_ms", nullable = false)
    private Long windowStartMs;

    @Column(name = "window_end_ms", nullable = false)
    private Long windowEndMs;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    /** (벡터 크기 - 1g)의 합, mg. */
    @Column(name = "magnitude_sum_mg", nullable = false)
    private Long magnitudeSumMg;

    /** (벡터 크기 - 1g)의 제곱합, mg². */
    @Column(name = "magnitude_sq_sum_mg", nullable = false)
    private Long magnitudeSqSumMg;

    /** 샘플이 모자라 정하지 않은 구간은 null이다. */
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "state", length = 16)
    private State state;

    public static ActivityWindow empty(Long memberId, long windowStartMs, long windowEndMs) {
        ActivityWindow window = new ActivityWindow();
        window.memberId = memberId;
        window.windowStartMs = windowStartMs;
        window.windowEndMs = windowEndMs;
        window.sampleCount = 0;
        window.magnitudeSumMg = 0L;
        window.magnitudeSqSumMg = 0L;
        return window;
    }

    public void add(int count, long sumMg, long sqSumMg, State recalculated) {
        this.sampleCount += count;
        this.magnitudeSumMg += sumMg;
        this.magnitudeSqSumMg += sqSumMg;
        this.state = recalculated;
    }

    /** 모집단 표준편차(g). 합계만으로 구하므로 묶음 도착 순서와 무관하다. */
    public static double magnitudeStdG(long count, long sumMg, long sqSumMg) {
        if (count == 0) {
            return 0.0;
        }
        // n·Σx² - (Σx)²는 정수로 정확히 구한다. 나눗셈 전에 빼야 경계값(0.04g)이 부동소수 오차로 흔들리지 않는다.
        double varianceMg2 = (double) (count * sqSumMg - sumMg * sumMg) / ((double) count * count);
        return Math.sqrt(Math.max(varianceMg2, 0.0)) / MG_PER_G;
    }
}
