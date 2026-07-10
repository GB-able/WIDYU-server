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
import java.time.LocalDate;
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

    public static final long COMPLETION_GRACE_MINUTES = 30;

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
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

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
    private HealthSchedule(Member member, String scheduleName, String placeAddress, Double latitude,
                          Double longitude, LocalDateTime scheduledAt, ProgressStatus progressStatus,
                          Integer rewardPoint, Boolean isReward, Status status) {
        this.member = member;
        this.scheduleName = scheduleName;
        this.placeAddress = placeAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scheduledAt = scheduledAt;
        this.progressStatus = ProgressStatus.UPCOMING;
        if (progressStatus != null) {
            this.progressStatus = progressStatus;
        }
        this.rewardPoint = 100;
        if (rewardPoint != null) {
            this.rewardPoint = rewardPoint;
        }
        this.isReward = false;
        if (isReward != null) {
            this.isReward = isReward;
        }
        this.status = Status.ACTIVE;
        if (status != null) {
            this.status = status;
        }
    }

    public static HealthSchedule create(Member member, String scheduleName, String placeAddress, Double latitude,
                                       Double longitude, LocalDateTime scheduledAt) {
        return HealthSchedule.builder()
                .member(member)
                .scheduleName(scheduleName)
                .placeAddress(placeAddress)
                .latitude(latitude)
                .longitude(longitude)
                .scheduledAt(scheduledAt)
                .build();
    }

    public void update(String scheduleName, String placeAddress, Double latitude,
                      Double longitude, LocalDateTime scheduledAt, ProgressStatus progressStatus) {
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

    public void complete() {
        this.progressStatus = ProgressStatus.COMPLETED;
    }

    public boolean canCompleteAt(LocalDateTime now) {
        LocalDateTime completionStart = scheduledAt.toLocalDate().atStartOfDay();
        LocalDateTime completionEnd = scheduledAt.plusMinutes(COMPLETION_GRACE_MINUTES);

        if (now.isBefore(completionStart)) {
            return false;
        }

        return !now.isAfter(completionEnd);
    }

    public void markIncomplete() {
        this.progressStatus = ProgressStatus.INCOMPLETE;
    }

    /**
     * 조회 시점 기준의 진행 상태.
     * 저장된 상태가 UPCOMING이라도 예정일이 이미 지났다면 INCOMPLETE로 간주한다.
     * (자정 배치가 아직 반영하지 못한 지난 일정도 올바르게 표시하기 위함)
     */
    public ProgressStatus getDisplayProgressStatus() {
        if (progressStatus == ProgressStatus.UPCOMING
                && scheduledAt.toLocalDate().isBefore(LocalDate.now())) {
            return ProgressStatus.INCOMPLETE;
        }
        return progressStatus;
    }
}
