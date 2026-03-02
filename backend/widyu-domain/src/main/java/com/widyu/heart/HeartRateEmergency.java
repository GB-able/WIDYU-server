package com.widyu.heart;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "heart_rate_emergency")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeartRateEmergency extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "heart_rate_emergency_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "heart_rate", nullable = false)
    private Integer heartRate;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "location", length = 500)
    private String location;

    @Builder(access = AccessLevel.PRIVATE)
    private HeartRateEmergency(Member member, Integer heartRate, LocalDateTime measuredAt, String location) {
        this.member = member;
        this.heartRate = heartRate;
        this.measuredAt = measuredAt;
        this.location = location;
    }

    public static HeartRateEmergency of(Member member, Integer heartRate, LocalDateTime measuredAt, String location) {
        return HeartRateEmergency.builder()
                .member(member)
                .heartRate(heartRate)
                .measuredAt(measuredAt)
                .location(location)
                .build();
    }
}
