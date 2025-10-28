package com.widyu.member;

import com.widyu.global.entity.BaseTimeEntity;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "family_connection")
public class FamilyConnection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false)
    private SeniorProfile senior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id", nullable = false)
    private Member guardian;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    private String nickname;

    @Builder(access = AccessLevel.PRIVATE)
    private FamilyConnection(SeniorProfile senior, Member guardian, LocalDateTime connectedAt, String nickname) {
        this.senior = senior;
        this.guardian = guardian;
        this.connectedAt = connectedAt;
        this.nickname = nickname;
    }

    public static FamilyConnection createConnection(SeniorProfile senior, Member guardian) {
        return FamilyConnection.builder()
                .senior(senior)
                .guardian(guardian)
                .connectedAt(LocalDateTime.now())
                .build();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}