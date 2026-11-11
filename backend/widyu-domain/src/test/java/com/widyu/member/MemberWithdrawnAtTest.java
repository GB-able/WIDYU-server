package com.widyu.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.entity.Status;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberWithdrawnAtTest {

    @Test
    @DisplayName("회원이 탈퇴하면 비활성이 되고 탈퇴 시각이 남는다")
    void 회원이_탈퇴하면_비활성이_되고_탈퇴_시각이_남는다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01000000000");
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);

        // when
        member.markWithdrawn(now);

        // then
        assertThat(member.getStatus()).isEqualTo(Status.INACTIVE);
        assertThat(member.getWithdrawnAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("관리자가 회원을 비활성으로 바꾸면 탈퇴 시각을 남기지 않는다")
    void 관리자가_회원을_비활성으로_바꾸면_탈퇴_시각을_남기지_않는다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01000000000");

        // when
        member.withdraw();

        // then
        assertThat(member.getStatus()).isEqualTo(Status.INACTIVE);
        assertThat(member.getWithdrawnAt()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 회원을 되살리면 탈퇴 시각을 비운다")
    void 탈퇴한_회원을_되살리면_탈퇴_시각을_비운다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01000000000");
        member.markWithdrawn(LocalDateTime.of(2026, 11, 12, 10, 0));

        // when
        member.reactivate();

        // then
        assertThat(member.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(member.getWithdrawnAt()).isNull();
    }
}
