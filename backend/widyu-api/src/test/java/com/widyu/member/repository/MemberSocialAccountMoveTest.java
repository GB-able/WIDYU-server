package com.widyu.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
import java.time.LocalDateTime;
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
@DisplayName("소셜 계정을 다른 회원으로 옮기는 UPDATE 테스트")
class MemberSocialAccountMoveTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("애플 계정을 기존 보호자로 옮긴 뒤 빈 회원을 탈퇴 처리해도 옮긴 계정은 지워지거나 가려지지 않는다")
    void 애플_계정을_옮긴_뒤_빈_회원을_탈퇴_처리해도_옮긴_계정이_남는다() {
        // given
        Member appleMember = Member.createMember(MemberType.GUARDIAN, "애플유저", null);
        appleMember.getSocialAccounts().add(
                SocialAccount.createSocialAccount("apple@apple.com", "APPLE", "apple-oauth-id", appleMember));
        entityManager.persist(appleMember);
        Member owner = Member.createMember(MemberType.GUARDIAN, "기존회원", "01000000002");
        entityManager.persistAndFlush(owner);

        // when
        int moved = memberRepository.moveSocialAccount(appleMember, owner, "APPLE");
        Member emptied = memberRepository.findById(appleMember.getId()).orElseThrow();
        emptied.maskPersonalInfo();
        emptied.markWithdrawn(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(moved).isEqualTo(1);
        Member reloadedOwner = memberRepository.findWithAllAccountsById(owner.getId()).orElseThrow();
        assertThat(reloadedOwner.getSocialAccounts()).hasSize(1);
        SocialAccount account = reloadedOwner.getSocialAccounts().get(0);
        assertThat(account.getOauthId()).isEqualTo("apple-oauth-id");
        assertThat(account.getEmail()).isEqualTo("apple@apple.com");
        assertThat(memberRepository.findWithAllAccountsById(appleMember.getId()).orElseThrow().getSocialAccounts())
                .isEmpty();
        assertThat(memberRepository.findMemberIdByProviderAndOauthId("APPLE", "apple-oauth-id"))
                .contains(owner.getId());
    }

    @Test
    @DisplayName("옮길 제공자의 계정이 없으면 0건을 갱신한다")
    void 옮길_계정이_없으면_0건을_갱신한다() {
        // given
        Member source = Member.createMember(MemberType.GUARDIAN, "카카오유저", null);
        source.getSocialAccounts().add(
                SocialAccount.createSocialAccount("kakao@kakao.com", "KAKAO", "kakao-id", source));
        entityManager.persist(source);
        Member target = Member.createMember(MemberType.GUARDIAN, "기존회원", "01000000002");
        entityManager.persistAndFlush(target);

        // when
        int moved = memberRepository.moveSocialAccount(source, target, "APPLE");

        // then
        assertThat(moved).isZero();
    }
}
