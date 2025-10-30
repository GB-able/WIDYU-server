package com.widyu.addressbookmark.repository;

import com.widyu.addressbookmark.AddressBookmark;
import com.widyu.member.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressBookmarkRepository extends JpaRepository<AddressBookmark, Long> {

    boolean existsByMemberAndRoadAddress(Member member, String roadAddress);

    Optional<AddressBookmark> findByIdAndMember(Long id, Member member);

    List<AddressBookmark> findAllByMember(Member member);
}
