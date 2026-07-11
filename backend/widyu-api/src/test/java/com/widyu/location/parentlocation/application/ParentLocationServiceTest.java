package com.widyu.location.parentlocation.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.location.parentlocation.dto.request.ParentLocationCreateRequest;
import com.widyu.location.parentlocation.dto.request.ParentLocationUpdateRequest;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParentLocationService 예외 처리 단위 테스트")
class ParentLocationServiceTest {

    @Mock private ParentLocationRepository parentLocationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks
    private ParentLocationService parentLocationService;

    @Test
    @DisplayName("HOME 타입 장소 생성 시 BAD_REQUEST 예외를 던지고 저장하지 않는다")
    void HOME_타입_장소_생성_시_예외가_발생한다() {
        // given
        ParentLocationCreateRequest request = new ParentLocationCreateRequest(
                1L, LocationType.HOME, "서울시 강남구", 37.5, 127.0, "집");

        // when & then
        assertThatThrownBy(() -> parentLocationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("집 주소는 마이페이지에서만 수정할 수 있습니다.");
        then(parentLocationRepository).should(never()).save(any(ParentLocation.class));
    }

    @Test
    @DisplayName("이미 등록된 주소 생성 시 BAD_REQUEST 예외를 던지고 저장하지 않는다")
    void 이미_등록된_주소_생성_시_예외가_발생한다() {
        // given
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ParentLocationCreateRequest request = new ParentLocationCreateRequest(
                1L, LocationType.OTHER, "서울시 강남구", 37.5, 127.0, "병원");

        given(memberRepository.findById(1L)).willReturn(Optional.of(senior));
        given(parentLocationRepository.existsByMemberAndPlaceAddress(senior, "서울시 강남구")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> parentLocationService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("이미 등록된 주소입니다.");
        then(parentLocationRepository).should(never()).save(any(ParentLocation.class));
    }

    @Test
    @DisplayName("HOME 타입 장소 삭제 시 BAD_REQUEST 예외를 던지고 삭제하지 않는다")
    void HOME_타입_장소_삭제_시_예외가_발생한다() {
        // given
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ParentLocation home = ParentLocation.builder()
                .member(senior)
                .locationType(LocationType.HOME)
                .placeAddress("서울시 강남구")
                .latitude(37.5)
                .longitude(127.0)
                .name("집")
                .build();

        given(memberRepository.findById(1L)).willReturn(Optional.of(senior));
        given(parentLocationRepository.findByIdAndMember(10L, senior)).willReturn(Optional.of(home));

        // when & then
        assertThatThrownBy(() -> parentLocationService.delete(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("집 주소는 마이페이지에서만 수정할 수 있습니다.");
        then(parentLocationRepository).should(never()).delete(any(ParentLocation.class));
    }

    @Test
    @DisplayName("존재하지 않는 장소 수정 시 BAD_REQUEST 예외를 던진다")
    void 존재하지_않는_장소_수정_시_예외가_발생한다() {
        // given
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ParentLocationUpdateRequest request = new ParentLocationUpdateRequest(
                1L, LocationType.OTHER, "서울시 강남구", 37.5, 127.0, "병원");

        given(memberRepository.findById(1L)).willReturn(Optional.of(senior));
        given(parentLocationRepository.findByIdAndMember(10L, senior)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> parentLocationService.update(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("존재하지 않는 장소입니다.");
    }
}
