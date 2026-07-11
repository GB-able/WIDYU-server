package com.widyu.global.aspect;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.widyu.global.annotation.ValidateFamilyAccess;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Optional;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyAccessAspect 예외 처리 단위 테스트")
class FamilyAccessAspectTest {

    @Mock private MemberUtil memberUtil;
    @Mock private MemberRepository memberRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;

    @InjectMocks
    private FamilyAccessAspect familyAccessAspect;

    @Test
    @DisplayName("검증 대상 파라미터가 없으면 BAD_REQUEST 예외를 던진다")
    void 검증_대상_파라미터가_없으면_예외가_발생한다() throws Exception {
        // given
        Member guardian = member(1L, MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(guardian);

        Method method = DummyController.class.getDeclaredMethod("missingParam", Long.class);
        JoinPoint joinPoint = joinPoint(method, 2L);
        ValidateFamilyAccess annotation = method.getAnnotation(ValidateFamilyAccess.class);

        // when & then
        assertThatThrownBy(() -> familyAccessAspect.validateFamilyAccess(joinPoint, annotation))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("파라미터 'memberId'를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("시니어가 다른 회원 리소스 접근 시 FORBIDDEN 예외를 던진다")
    void 시니어가_다른_회원_리소스_접근_시_예외가_발생한다() throws Exception {
        // given
        Member senior = member(1L, MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(senior);

        Method method = DummyController.class.getDeclaredMethod("withMemberId", Long.class);
        JoinPoint joinPoint = joinPoint(method, 2L);

        // when & then
        assertThatThrownBy(() -> familyAccessAspect.validateFamilyAccess(
                joinPoint, method.getAnnotation(ValidateFamilyAccess.class)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("보호자만 다른 사용자의 리소스에 접근할 수 있습니다.");
        then(memberRepository).should(never()).findById(2L);
    }

    @Test
    @DisplayName("대상 회원이 없으면 BAD_REQUEST 예외를 던진다")
    void 대상_회원이_없으면_예외가_발생한다() throws Exception {
        // given
        Member guardian = member(1L, MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(2L)).willReturn(Optional.empty());

        Method method = DummyController.class.getDeclaredMethod("withMemberId", Long.class);

        // when & then
        assertThatThrownBy(() -> familyAccessAspect.validateFamilyAccess(
                joinPoint(method, 2L), method.getAnnotation(ValidateFamilyAccess.class)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("가족으로 연결되지 않은 시니어 접근 시 FORBIDDEN 예외를 던진다")
    void 가족으로_연결되지_않은_시니어_접근_시_예외가_발생한다() throws Exception {
        // given
        Member guardian = member(1L, MemberType.GUARDIAN);
        Member senior = member(2L, MemberType.SENIOR);
        SeniorProfile seniorProfile = seniorProfile(10L, senior);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(2L)).willReturn(Optional.of(senior));
        given(familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(1L, 10L)).willReturn(false);

        Method method = DummyController.class.getDeclaredMethod("withMemberId", Long.class);

        // when & then
        assertThatThrownBy(() -> familyAccessAspect.validateFamilyAccess(
                joinPoint(method, 2L), method.getAnnotation(ValidateFamilyAccess.class)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("가족으로 연결된 시니어만 접근할 수 있습니다.");
        then(familyMembershipRepository).should().existsByGuardianIdAndSeniorProfileId(1L, seniorProfile.getId());
    }

    private JoinPoint joinPoint(Method method, Object... args) {
        MethodSignature signature = mock(MethodSignature.class);
        given(signature.getMethod()).willReturn(method);

        JoinPoint joinPoint = mock(JoinPoint.class);
        given(joinPoint.getSignature()).willReturn(signature);
        given(joinPoint.getArgs()).willReturn(args);
        return joinPoint;
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

    static class DummyController {

        @ValidateFamilyAccess(memberIdParam = "memberId")
        void withMemberId(Long memberId) {
        }

        @ValidateFamilyAccess(memberIdParam = "memberId")
        void missingParam(Long seniorId) {
        }
    }
}
