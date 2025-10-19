package com.widyu.member.repository;

import static com.widyu.member.QMember.member;
import static com.widyu.member.QSocialAccount.socialAccount;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.member.Member;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Member> findByProviderAndOauthId(String provider, String oauthId) {
        Member result = queryFactory
                .selectFrom(member)
                .join(member.socialAccounts, socialAccount).fetchJoin()
                .where(
                        socialAccount.provider.eq(provider),
                        socialAccount.oauthId.eq(oauthId)
                )
                .distinct()
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Long> findMemberIdByProviderAndOauthId(String provider, String oauthId) {
        Long id = queryFactory
                .select(member.id)
                .from(member)
                .join(member.socialAccounts, socialAccount)
                .where(
                        socialAccount.provider.eq(provider),
                        socialAccount.oauthId.eq(oauthId)
                )
                .fetchOne();
        return Optional.ofNullable(id);
    }

    @Override
    public Optional<Member> findWithAllAccountsById(Long id) {
        Member result = queryFactory
                .selectFrom(member)
                .leftJoin(member.socialAccounts, socialAccount).fetchJoin()
                .where(member.id.eq(id))
                .distinct()
                .fetchOne();
        return Optional.ofNullable(result);
    }

}
