package com.widyu.domain.member.repository;

import com.widyu.domain.member.entity.Member;
import java.util.Optional;

public interface MemberRepositoryCustom {
    Optional<Member> findByProviderAndOauthId(String provider, String oauthId);
    Optional<Long> findMemberIdByProviderAndOauthId(String provider, String oauthId);
    Optional<Member> findWithAllAccountsById(Long id);
}
