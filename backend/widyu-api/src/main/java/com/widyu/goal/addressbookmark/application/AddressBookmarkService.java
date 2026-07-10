package com.widyu.goal.addressbookmark.application;

import com.widyu.addressbookmark.AddressBookmark;
import com.widyu.goal.addressbookmark.dto.request.AddressBookmarkCreateRequest;
import com.widyu.goal.addressbookmark.dto.response.AddressBookmarkIdResponse;
import com.widyu.goal.addressbookmark.dto.response.AddressBookmarkResponse;
import com.widyu.goal.addressbookmark.repository.AddressBookmarkRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressBookmarkService {

    private final AddressBookmarkRepository addressBookmarkRepository;
    private final MemberUtil memberUtil;

    public List<AddressBookmarkResponse> findAll() {
        Member member = memberUtil.getCurrentMember();
        return addressBookmarkRepository.findAllByMember(member)
            .stream()
            .map(AddressBookmarkResponse::of)
            .collect(Collectors.toList());
    }

    @Transactional
    public AddressBookmarkIdResponse create(AddressBookmarkCreateRequest request) {
        Member member = memberUtil.getCurrentMember();

        if (addressBookmarkRepository.existsByMemberAndRoadAddress(member, request.roadAddress())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 즐겨찾기에 추가된 주소입니다.");
        }

        AddressBookmark saved = addressBookmarkRepository.save(request.toEntity(member));

        return AddressBookmarkIdResponse.of(saved);
    }

    @Transactional
    public void delete(Long addressBookmarkId) {
        Member member = memberUtil.getCurrentMember();

        AddressBookmark bookmark = addressBookmarkRepository.findByIdAndMember(addressBookmarkId, member)
            .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "이미 즐겨찾기 취소된 장소입니다."));

        addressBookmarkRepository.delete(bookmark);
    }
}
