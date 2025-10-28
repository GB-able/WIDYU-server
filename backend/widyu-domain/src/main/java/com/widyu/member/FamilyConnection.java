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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionStatus status;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    private String nickname;  // 보호자가 시니어에게 부여한 별칭 (예: "우리 엄마", "할머니")

    @Builder(access = AccessLevel.PRIVATE)
    private FamilyConnection(SeniorProfile senior, Member guardian, ConnectionRole role,
                            ConnectionStatus status, LocalDateTime connectedAt, String nickname) {
        this.senior = senior;
        this.guardian = guardian;
        this.role = role;
        this.status = status;
        this.connectedAt = connectedAt;
        this.nickname = nickname;
    }

    public static FamilyConnection createConnection(SeniorProfile senior, Member guardian,
                                                    ConnectionRole role) {
        return FamilyConnection.builder()
                .senior(senior)
                .guardian(guardian)
                .role(role)
                .status(ConnectionStatus.ACTIVE)
                .connectedAt(LocalDateTime.now())
                .build();
    }

    public static FamilyConnection createPendingConnection(SeniorProfile senior, Member guardian,
                                                          ConnectionRole role) {
        return FamilyConnection.builder()
                .senior(senior)
                .guardian(guardian)
                .role(role)
                .status(ConnectionStatus.PENDING)
                .connectedAt(LocalDateTime.now())
                .build();
    }

    public void activate() {
        this.status = ConnectionStatus.ACTIVE;
        if (this.connectedAt == null) {
            this.connectedAt = LocalDateTime.now();
        }
    }

    public void deactivate() {
        this.status = ConnectionStatus.INACTIVE;
    }

    public void reject() {
        this.status = ConnectionStatus.REJECTED;
    }

    public void updateRole(ConnectionRole newRole) {
        this.role = newRole;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isActive() {
        return this.status == ConnectionStatus.ACTIVE;
    }

    public boolean isPending() {
        return this.status == ConnectionStatus.PENDING;
    }
}