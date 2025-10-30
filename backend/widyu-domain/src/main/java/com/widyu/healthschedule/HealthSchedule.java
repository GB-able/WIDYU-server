package com.widyu.healthschedule;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.global.entity.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE health_schedule SET status = 'DELETED' WHERE health_schedule_id = ?")
@Where(clause = "status = 'ACTIVE'")
public class HealthSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "health_schedule_id")
    private Long id;

    @Column(name = "schedule_name", nullable = false)
    private String scheduleName;

    @Column(name = "place_address")
    private String placeAddress;

    @Column(name = "latitude")
    private String latitude;

    @Column(name = "longitude")
    private String longitude;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false)
    private ProgressStatus progressStatus = ProgressStatus.UPCOMING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Builder(access = AccessLevel.PRIVATE)
    private HealthSchedule(String scheduleName, String placeAddress, String latitude,
                          String longitude, LocalDateTime scheduledAt, ProgressStatus progressStatus, Status status) {
        this.scheduleName = scheduleName;
        this.placeAddress = placeAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scheduledAt = scheduledAt;
        this.progressStatus = progressStatus != null ? progressStatus : ProgressStatus.UPCOMING;
        this.status = status != null ? status : Status.ACTIVE;
    }

    public static HealthSchedule create(String scheduleName, String placeAddress, String latitude,
                                       String longitude, LocalDateTime scheduledAt) {
        return HealthSchedule.builder()
                .scheduleName(scheduleName)
                .placeAddress(placeAddress)
                .latitude(latitude)
                .longitude(longitude)
                .scheduledAt(scheduledAt)
                .build();
    }

    public void update(String scheduleName, String placeAddress, String latitude,
                      String longitude, LocalDateTime scheduledAt, ProgressStatus progressStatus) {
        if (scheduleName != null) {
            this.scheduleName = scheduleName;
        }
        if (placeAddress != null) {
            this.placeAddress = placeAddress;
        }
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
        if (scheduledAt != null) {
            this.scheduledAt = scheduledAt;
        }
        if (progressStatus != null) {
            this.progressStatus = progressStatus;
        }
    }

    public void delete() {
        this.status = Status.DELETED;
    }
}
