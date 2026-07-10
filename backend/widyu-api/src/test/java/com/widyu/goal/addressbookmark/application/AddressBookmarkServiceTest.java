package com.widyu.goal.addressbookmark.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.widyu.addressbookmark.AddressBookmark;
import com.widyu.global.error.BusinessException;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.addressbookmark.dto.request.AddressBookmarkCreateRequest;
import com.widyu.goal.addressbookmark.dto.response.AddressBookmarkIdResponse;
import com.widyu.goal.addressbookmark.repository.AddressBookmarkRepository;
import com.widyu.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressBookmarkService 생성 단위 테스트")
class AddressBookmarkServiceTest {

    @Mock private AddressBookmarkRepository addressBookmarkRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks private AddressBookmarkService addressBookmarkService;

    private AddressBookmarkCreateRequest createRequest() {
        return new AddressBookmarkCreateRequest(
                "서울특별시 마포구 성암로 301",
                "서울특별시 마포구 상암동 1595",
                "MBC",
                37.5789,
                126.8912,
                "성암로 301",
                "상암동 1595"
        );
    }

    @Test
    @DisplayName("즐겨찾기를 생성하면 저장된 addressBookmarkId를 반환한다")
    void 즐겨찾기를_생성하면_저장된_id를_반환한다() {
        // given
        Member member = mock(Member.class);
        AddressBookmarkCreateRequest request = createRequest();
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(addressBookmarkRepository.existsByMemberAndRoadAddress(member, request.roadAddress()))
                .willReturn(false);
        given(addressBookmarkRepository.save(any(AddressBookmark.class))).willAnswer(invocation -> {
            AddressBookmark saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        // when
        AddressBookmarkIdResponse response = addressBookmarkService.create(request);

        // then
        assertThat(response.addressBookmarkId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("이미 즐겨찾기된 주소를 생성하면 예외가 발생하고 저장하지 않는다")
    void 이미_즐겨찾기된_주소는_예외가_발생한다() {
        // given
        Member member = mock(Member.class);
        AddressBookmarkCreateRequest request = createRequest();
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(addressBookmarkRepository.existsByMemberAndRoadAddress(member, request.roadAddress()))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> addressBookmarkService.create(request))
                .isInstanceOf(BusinessException.class);
        then(addressBookmarkRepository).should(never()).save(any(AddressBookmark.class));
    }
}
