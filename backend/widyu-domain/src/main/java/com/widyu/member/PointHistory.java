package com.widyu.member;

import com.widyu.global.entity.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "point_history")
public class PointHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_profile_id", nullable = false)
    private SeniorProfile seniorProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointHistoryType type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String description;

    @Column(name = "operation_key", unique = true, length = 100)
    private String operationKey;

    @Builder(access = AccessLevel.PRIVATE)
    private PointHistory(SeniorProfile seniorProfile, PointHistoryType type, Long amount, String description,
                         String operationKey) {
        this.seniorProfile = seniorProfile;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.operationKey = operationKey;
    }

    public static PointHistory earn(SeniorProfile seniorProfile, Long amount, String description) {
        return earn(seniorProfile, amount, description, null);
    }

    public static PointHistory earn(SeniorProfile seniorProfile, Long amount, String description, String operationKey) {
        return PointHistory.builder()
                .seniorProfile(seniorProfile)
                .type(PointHistoryType.EARN)
                .amount(amount)
                .description(description)
                .operationKey(operationKey)
                .build();
    }

    public static PointHistory use(SeniorProfile seniorProfile, Long amount, String description) {
        return use(seniorProfile, amount, description, null);
    }

    public static PointHistory use(SeniorProfile seniorProfile, Long amount, String description, String operationKey) {
        return PointHistory.builder()
                .seniorProfile(seniorProfile)
                .type(PointHistoryType.USE)
                .amount(amount)
                .description(description)
                .operationKey(operationKey)
                .build();
    }
}
