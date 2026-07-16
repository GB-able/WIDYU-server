package com.widyu.member.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyAccessService 가족 접근 검증 단위 테스트")
class FamilyAccessServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;

    @InjectMocks
    private FamilyAccessService familyAccessService;

    @Test
    @DisplayName("가족으로 연결된 시니어에게 접근하면 예외 없이 통과한다")
    void 가족으로_연결된_시니어_접근_시_예외없이_통과한다() {
        // given
        Member senior = member(2L, MemberType.SENIOR);
        SeniorProfile seniorProfile = seniorProfile(10L, senior);
        given(memberRepository.findById(2L)).willReturn(Optional.of(senior));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(true);

        // when & then
        assertThatCode(() -> familyAccessService.verifyFamilyAccess(1L, 2L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("존재하지 않는 targetMemberId로 접근하면 BAD_REQUEST 예외를 던진다")
    void 존재하지_않는_대상_회원_접근_시_예외가_발생한다() {
        // given
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> familyAccessService.verifyFamilyAccess(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("targetMember가 SENIOR가 아닌 경우 BAD_REQUEST 예외를 던진다")
    void 대상_회원이_시니어가_아닌_경우_예외가_발생한다() {
        // given
        Member guardian = member(2L, MemberType.GUARDIAN);
        given(memberRepository.findById(2L)).willReturn(Optional.of(guardian));

        // when & then
        assertThatThrownBy(() -> familyAccessService.verifyFamilyAccess(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("시니어의 리소스만 접근할 수 있습니다.");
    }

    @Test
    @DisplayName("가족 관계가 없는 시니어 접근 시 FORBIDDEN 예외를 던진다")
    void 가족_관계가_없는_시니어_접근_시_예외가_발생한다() {
        // given
        Member senior = member(2L, MemberType.SENIOR);
        SeniorProfile seniorProfile = seniorProfile(10L, senior);
        given(memberRepository.findById(2L)).willReturn(Optional.of(senior));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> familyAccessService.verifyFamilyAccess(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("가족으로 연결된 시니어만 접근할 수 있습니다.");
    }

    private Member member(Long id, MemberType type) {
        Member member = Member.createMember(type, type.name(), "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private SeniorProfile seniorProfile(Long id, Member member) {
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                member, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(seniorProfile, "id", id);
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        return seniorProfile;
    }
}
