package com.widyu.healthschedule;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

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

    @Column(name = "reward_point", nullable = false)
    private Integer rewardPoint = 100;

    @Column(name = "is_reward", nullable = false)
    private Boolean isReward = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Builder(access = AccessLevel.PRIVATE)
    private HealthSchedule(Member member, String scheduleName, String placeAddress, String latitude,
                          String longitude, LocalDateTime scheduledAt, ProgressStatus progressStatus,
                          Integer rewardPoint, Boolean isReward, Status status) {
        this.member = member;
        this.scheduleName = scheduleName;
        this.placeAddress = placeAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scheduledAt = scheduledAt;
        this.progressStatus = progressStatus != null ? progressStatus : ProgressStatus.UPCOMING;
        this.rewardPoint = rewardPoint != null ? rewardPoint : 100;
        this.isReward = isReward != null ? isReward : false;
        this.status = status != null ? status : Status.ACTIVE;
    }

    public static HealthSchedule create(Member member, String scheduleName, String placeAddress, String latitude,
                                       String longitude, LocalDateTime scheduledAt) {
        return HealthSchedule.builder()
                .member(member)
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

    public void claimReward() {
        this.isReward = true;
    }
}
