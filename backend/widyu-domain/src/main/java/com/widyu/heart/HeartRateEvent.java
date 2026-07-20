package com.widyu.heart;

import com.widyu.global.entity.BaseTimeEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
    name = "heart_rate_event",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_heart_event_member_time",
        columnNames = {"member_id", "measured_at"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeartRateEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "heart_rate_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "heart_rate", nullable = false)
    private Integer heartRate;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HeartRateStatus status;

    @Builder(access = AccessLevel.PRIVATE)
    private HeartRateEvent(Member member, Integer heartRate, LocalDateTime measuredAt, HeartRateStatus status) {
        this.member = member;
        this.heartRate = heartRate;
        this.measuredAt = measuredAt;
        this.status = status;
    }

    public static HeartRateEvent of(Member member, Integer heartRate, LocalDateTime measuredAt, HeartRateStatus status) {
        return HeartRateEvent.builder()
                .member(member)
                .heartRate(heartRate)
                .measuredAt(measuredAt)
                .status(status)
                .build();
    }
}
