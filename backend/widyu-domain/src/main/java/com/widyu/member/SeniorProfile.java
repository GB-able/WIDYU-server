package com.widyu.member;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "senior_profile")
public class SeniorProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(name = "birth_date")
    private String birthDate;

    private String address;

    @Column(name = "detail_address")
    private String detailAddress;

    @Column(name = "invite_code", nullable = false, unique = true, length = 7)
    private String inviteCode;

    @Column(nullable = false)
    private Long points = 0L;

    @Column(name = "default_walk_goal")
    private Integer defaultWalkGoal;

    @OneToMany(mappedBy = "senior", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FamilyConnection> familyConnections = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private SeniorProfile(Member member, String birthDate, String address, String detailAddress,
                          String inviteCode, Long points, Integer defaultWalkGoal) {
        this.member = member;
        this.birthDate = birthDate;
        this.address = address;
        this.detailAddress = detailAddress;
        this.inviteCode = inviteCode;
        this.points = points;
        this.defaultWalkGoal = defaultWalkGoal;
    }

    public static SeniorProfile createSeniorProfile(Member member, String birthDate, String address,
                                                    String detailAddress, String inviteCode) {
        return SeniorProfile.builder()
                .member(member)
                .birthDate(birthDate)
                .address(address)
                .detailAddress(detailAddress)
                .inviteCode(inviteCode)
                .points(100L)  // 초기 포인트 100점
                .defaultWalkGoal(null)  // 초기 걷기 목표는 없음
                .build();
    }

    public void addPoints(Long additionalPoints) {
        if (additionalPoints != null && additionalPoints > 0) {
            this.points += additionalPoints;
        }
    }

    public void deductPoints(Long deductionPoints) {
        if (deductionPoints != null && deductionPoints > 0 && this.points >= deductionPoints) {
            this.points -= deductionPoints;
        }
    }

    public boolean hasEnoughPoints(Long requiredPoints) {
        return this.points >= requiredPoints;
    }

    public void updateDefaultWalkGoal(Integer walkGoal) {
        this.defaultWalkGoal = walkGoal;
    }

    public boolean hasDefaultWalkGoal() {
        return this.defaultWalkGoal != null;
    }
}