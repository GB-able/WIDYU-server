package com.widyu.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
import jakarta.persistence.EntityManagerFactory;
import java.util.Optional;
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
@DisplayName("소셜 계정으로 회원을 찾는 JPQL 조회 테스트")
class MemberSocialAccountQueryTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("제공자와 oauthId로 조회하면 소셜 계정이 초기화된 회원을 반환한다")
    void 제공자와_oauthId로_소셜_계정이_초기화된_회원을_찾는다() {
        // given
        Member member = persistGuardianWithAccounts("kakao", "kakao-1", "naver", "naver-1");
        entityManager.clear();

        // when
        Optional<Member> found = memberRepository.findByProviderAndOauthId("kakao", "kakao-1");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(member.getId());
        assertThat(isLoaded(found.get())).isTrue();
        assertThat(found.get().getSocialAccounts())
                .extracting(SocialAccount::getOauthId)
                .containsExactly("kakao-1");
    }

    @Test
    @DisplayName("일치하는 소셜 계정이 없으면 빈 값을 반환한다")
    void 일치하는_소셜_계정이_없으면_빈_값을_반환한다() {
        // given
        persistGuardianWithAccounts("kakao", "kakao-1", "naver", "naver-1");
        entityManager.clear();

        // when
        Optional<Member> byOauthId = memberRepository.findByProviderAndOauthId("kakao", "naver-1");
        Optional<Long> idOnly = memberRepository.findMemberIdByProviderAndOauthId("apple", "kakao-1");

        // then
        assertThat(byOauthId).isEmpty();
        assertThat(idOnly).isEmpty();
    }

    @Test
    @DisplayName("제공자와 oauthId로 id만 조회하면 해당 회원 id를 반환한다")
    void 제공자와_oauthId로_회원_id만_찾는다() {
        // given
        Member member = persistGuardianWithAccounts("kakao", "kakao-1", "naver", "naver-1");
        persistGuardianWithAccounts("kakao", "kakao-2", "naver", "naver-2");
        entityManager.clear();

        // when
        Optional<Long> found = memberRepository.findMemberIdByProviderAndOauthId("naver", "naver-1");

        // then
        assertThat(found).contains(member.getId());
    }

    @Test
    @DisplayName("id로 계정 전체를 조회하면 모든 소셜 계정을 중복 없이 담은 회원을 반환한다")
    void id로_모든_소셜_계정을_중복_없이_담은_회원을_찾는다() {
        // given
        Member member = persistGuardianWithAccounts("kakao", "kakao-1", "naver", "naver-1");
        entityManager.clear();

        // when
        Optional<Member> found = memberRepository.findWithAllAccountsById(member.getId());

        // then
        assertThat(found).isPresent();
        assertThat(isLoaded(found.get())).isTrue();
        assertThat(found.get().getSocialAccounts())
                .extracting(SocialAccount::getOauthId)
                .containsExactlyInAnyOrder("kakao-1", "naver-1");
    }

    @Test
    @DisplayName("소셜 계정이 없는 회원을 id로 조회해도 회원을 반환한다")
    void 소셜_계정이_없는_회원도_id로_찾는다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", null);
        entityManager.persistAndFlush(member);
        entityManager.clear();

        // when
        Optional<Member> found = memberRepository.findWithAllAccountsById(member.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getSocialAccounts()).isEmpty();
    }

    private boolean isLoaded(Member member) {
        return entityManagerFactory.getPersistenceUnitUtil().isLoaded(member, "socialAccounts");
    }

    private Member persistGuardianWithAccounts(String provider1, String oauthId1, String provider2, String oauthId2) {
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", null);
        entityManager.persist(member);
        entityManager.persist(SocialAccount.createSocialAccount(null, provider1, oauthId1, member));
        entityManager.persist(SocialAccount.createSocialAccount(null, provider2, oauthId2, member));
        entityManager.flush();
        return member;
    }
}
