package com.widyu.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@DisplayName("소셜 계정 이메일 회원 조회 테스트")
class MemberSocialEmailQueryTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("같은 이메일의 소셜 계정을 가진 회원이 셋이면 단건 조회 예외 없이 두 건을 반환한다")
    void 같은_이메일의_회원이_여럿이면_두_건을_반환한다() {
        // given
        persist("회원1", "shared@example.com", "kakao", "naver");
        persist("회원2", "shared@example.com", "kakao");
        persist("회원3", "shared@example.com", "APPLE");
        entityManager.clear();

        // when
        List<Member> members = memberRepository.findDistinctTop2BySocialAccounts_Email("shared@example.com");

        // then
        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("한 회원의 소셜 계정 둘이 같은 이메일이면 그 회원 한 건만 반환한다")
    void 한_회원의_소셜_계정_둘이_같은_이메일이면_한_건을_반환한다() {
        // given
        Member owner = persist("주인", "owner@example.com", "kakao", "naver");
        entityManager.clear();

        // when
        List<Member> members = memberRepository.findDistinctTop2BySocialAccounts_Email("owner@example.com");

        // then
        assertThat(members).extracting(Member::getId).containsExactly(owner.getId());
    }

    private Member persist(String name, String email, String... providers) {
        Member member = Member.createMember(MemberType.GUARDIAN, name, null);
        for (String provider : providers) {
            member.getSocialAccounts().add(
                    SocialAccount.createSocialAccount(email, provider, name + "-" + provider, member));
        }
        entityManager.persistAndFlush(member);
        return member;
    }
}
