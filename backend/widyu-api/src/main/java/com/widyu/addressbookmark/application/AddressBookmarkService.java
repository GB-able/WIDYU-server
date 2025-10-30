package com.widyu.addressbookmark.application;

import com.widyu.addressbookmark.AddressBookmark;
import com.widyu.addressbookmark.dto.request.AddressBookmarkCreateRequest;
import com.widyu.addressbookmark.repository.AddressBookmarkRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressBookmarkService {

    private final AddressBookmarkRepository addressBookmarkRepository;
    private final MemberUtil memberUtil;

    @Transactional
    public AddressBookmark create(AddressBookmarkCreateRequest request) {
        Member member = memberUtil.getCurrentMember();

        AddressBookmark addressBookmark = request.toEntity(member);
        return addressBookmarkRepository.save(addressBookmark);
    }
}
