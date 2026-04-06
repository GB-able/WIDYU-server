package com.widyu.fcm;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"member_id", "category"})
})
public class MemberNotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FcmCategory category;

    @Column(nullable = false)
    private boolean enabled;

    @Builder(access = AccessLevel.PRIVATE)
    private MemberNotificationSetting(Member member, FcmCategory category, boolean enabled) {
        this.member = member;
        this.category = category;
        this.enabled = enabled;
    }

    public static MemberNotificationSetting create(Member member, FcmCategory category, boolean enabled) {
        return MemberNotificationSetting.builder()
                .member(member)
                .category(category)
                .enabled(enabled)
                .build();
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}