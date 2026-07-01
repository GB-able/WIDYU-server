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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    private String address;

    @Column(name = "invite_code", nullable = false, length = 7)
    private String inviteCode;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false)
    private Long points = 0L;

    @Column(name = "default_walk_goal")
    private Integer defaultWalkGoal;

    @Builder(access = AccessLevel.PRIVATE)
    private SeniorProfile(Member member, Family family, String address,
                          String inviteCode, LocalDate birthDate, Long points, Integer defaultWalkGoal) {
        this.member = member;
        this.family = family;
        this.address = address;
        this.inviteCode = inviteCode;
        this.birthDate = birthDate;
        this.points = points;
        this.defaultWalkGoal = defaultWalkGoal;
    }

    public static SeniorProfile createSeniorProfile(Member member, Family family, String address,
                                                    String inviteCode, LocalDate birthDate) {
        return SeniorProfile.builder()
                .member(member)
                .family(family)
                .address(address)
                .inviteCode(inviteCode)
                .birthDate(birthDate)
                .points(100L)
                .defaultWalkGoal(null)
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

    public void updateAddress(String address) {
        this.address = address;
    }

    public void updateInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
