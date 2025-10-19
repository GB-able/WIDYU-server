package com.widyu.member.repository;

import com.widyu.member.Member;
import java.util.Optional;

public interface MemberRepositoryCustom {
    Optional<Member> findByProviderAndOauthId(String provider, String oauthId);
    Optional<Long> findMemberIdByProviderAndOauthId(String provider, String oauthId);
    Optional<Member> findWithAllAccountsById(Long id);
}
