package com.widyu.location.stay;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 시니어 한 명의 체류와 안전구역 판정 상태(LLD-0141, ADR-0044). 판정의 권위 있는 저장소이며, 같은 DB 트랜잭션에서
 * 알림 Outbox와 함께 커밋되거나 함께 롤백된다. 이전에는 Redis 체류 키와 알림 표식 키였다.
 *
 * <p>좌표와 시작 시각은 체류가 시작된 지점이다. 같은 자리(30m 안) 위치는 이 행을 바꾸지 않는다.
 * 보호자에게 보이는 체류 시간은 {@link #zoneChangedAt}부터 센다(LLD-0191).
 */
@Getter
@Entity
@Table(name = "senior_stay_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeniorStayState extends BaseTimeEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "stay_started_at", nullable = false)
    private LocalDateTime stayStartedAt;

    /** 체류 지점이 들어간 안전구역 종류(HOME, OTHER). 안전구역 밖이면 null. */
    @Column(name = "location_type", length = 16)
    private String locationType;

    @Column(name = "location_name", length = 255)
    private String locationName;

    /**
     * 안전구역 상태(어느 안전구역 안, 또는 밖)가 마지막으로 바뀐 시각. 같은 구역 안이나 밖에서 30m 넘게 움직여도 그대로다.
     * 보호자 카드의 「N시간 N분 동안 휴식/활동 중」은 이 시각부터 센다(LLD-0191).
     */
    @Column(name = "zone_changed_at", nullable = false)
    private LocalDateTime zoneChangedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "alert_state", length = 16)
    private SafeZoneAlertState alertState;

    /** 알림 묶음이 끝나는 시각. 이 시각이 지나면 {@link #alertState}는 없는 것과 같다. */
    @Column(name = "alert_until")
    private LocalDateTime alertUntil;

    private SeniorStayState(Long memberId, Double latitude, Double longitude, LocalDateTime stayStartedAt,
                            String locationType, String locationName,
                            SafeZoneAlertState alertState, LocalDateTime alertUntil) {
        this.memberId = memberId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stayStartedAt = stayStartedAt;
        this.locationType = locationType;
        this.locationName = locationName;
        this.zoneChangedAt = stayStartedAt;
        this.alertState = alertState;
        this.alertUntil = alertUntil;
    }

    public static SeniorStayState start(Long memberId, Double latitude, Double longitude, LocalDateTime now,
                                        String locationType, String locationName) {
        return new SeniorStayState(memberId, latitude, longitude, now, locationType, locationName, null, null);
    }

    /** 기존 Redis 상태를 옮겨 올 때만 쓴다(LLD-0141 8절). 구역 상태가 바뀐 시각은 모르므로 체류 시작으로 둔다(LLD-0191). */
    public static SeniorStayState imported(Long memberId, Double latitude, Double longitude,
                                           LocalDateTime stayStartedAt, String locationType, String locationName,
                                           SafeZoneAlertState alertState, LocalDateTime alertUntil) {
        return new SeniorStayState(memberId, latitude, longitude, stayStartedAt, locationType, locationName,
                alertState, alertUntil);
    }

    /**
     * 새 체류 지점으로 옮긴다. 안전구역 알림 판정은 {@link #applySafeZoneTransition}이 따로 한다.
     *
     * <p>구역 상태가 바뀐 시각은 안전구역 종류나 이름이 바뀔 때만 지금으로 바꾼다. 안전구역을 하나도 등록하지 않았으면 구역
     * 상태가 바뀌지 않아 시간이 끝없이 늘어나므로 예전처럼 30m 이동마다 새로 센다(LLD-0191).
     */
    public void moveTo(Double latitude, Double longitude, LocalDateTime now, String locationType, String locationName,
                       boolean safeZonesRegistered) {
        if (!safeZonesRegistered || !Objects.equals(this.locationType, locationType)
                || !Objects.equals(this.locationName, locationName)) {
            this.zoneChangedAt = now;
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.stayStartedAt = now;
        this.locationType = locationType;
        this.locationName = locationName;
    }

    /**
     * 안전구역 밖으로 나가거나 돌아왔을 때 보낼 알림을 정하고 묶음 상태를 바꾼다. 규칙은 이전 Redis 표식과 같다(ADR-0039).
     * 이탈은 묶음이 없거나 끝났을 때만 알리고 30분 묶음을 연다. 복귀는 이탈 알림만 보낸 상태면 묶음이 끝났어도 한 번 알리고,
     * 그때부터 다시 30분 묶음을 연다. 귀가 직후 경계에서 위치가 흔들려도 이탈, 복귀를 또 알리지 않는다(LLD-0190).
     */
    public SafeZoneTransition applySafeZoneTransition(String previousType, String currentType, LocalDateTime now,
                                                      Duration window) {
        if (currentType != null) {
            if (previousType != null || alertState != SafeZoneAlertState.EXITED) {
                return SafeZoneTransition.NONE;
            }
            alertState = SafeZoneAlertState.RETURNED;
            alertUntil = now.plus(window);
            return SafeZoneTransition.RETURN_ALERT;
        }
        if (previousType == null || isAlertWindowOpen(now)) {
            return SafeZoneTransition.NONE;
        }
        alertState = SafeZoneAlertState.EXITED;
        alertUntil = now.plus(window);
        return SafeZoneTransition.EXIT_ALERT;
    }

    private boolean isAlertWindowOpen(LocalDateTime now) {
        return alertState != null && alertUntil != null && alertUntil.isAfter(now);
    }
}
