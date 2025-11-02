package com.widyu.global.aspect;

import com.widyu.global.annotation.ValidateFamilyAccess;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;


@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FamilyAccessAspect {

    private final MemberUtil memberUtil;
    private final MemberRepository memberRepository;
    private final FamilyConnectionRepository familyConnectionRepository;

    @Before("@annotation(validateFamilyAccess)")
    public void validateFamilyAccess(JoinPoint joinPoint, ValidateFamilyAccess validateFamilyAccess) {
        Member currentMember = memberUtil.getCurrentMember();
        String memberIdParamName = validateFamilyAccess.memberIdParam();

        // 메서드의 파라미터에서 memberId 값을 추출
        Long targetMemberId = extractMemberId(joinPoint, memberIdParamName);

        // memberId가 null이거나 본인 ID면 검증 불필요
        if (targetMemberId == null || targetMemberId.equals(currentMember.getId())) {
            return;
        }

        // 보호자인 경우에만 다른 사람(시니어)의 리소스 접근 가능
        if (currentMember.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "보호자만 다른 사용자의 리소스에 접근할 수 있습니다.");
        }

        // 대상 멤버 조회
        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 사용자입니다."));

        // 대상이 시니어가 아니면 에러
        if (targetMember.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어의 리소스만 접근할 수 있습니다.");
        }

        // 시니어 프로필 확인
        if (targetMember.getSeniorProfile() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어 프로필이 없습니다.");
        }

        // 가족 관계 확인
        boolean isFamily = familyConnectionRepository.existsBySeniorIdAndGuardianId(
                targetMember.getSeniorProfile().getId(),
                currentMember.getId()
        );

        if (!isFamily) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "가족으로 연결된 시니어만 접근할 수 있습니다.");
        }

        log.debug("가족 관계 검증 성공: guardianId={}, seniorId={}",
                currentMember.getId(), targetMemberId);
    }

    /**
     * JoinPoint에서 지정된 파라미터 이름에 해당하는 memberId 값을 추출합니다.
     */
    private Long extractMemberId(JoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                Object value = args[i];
                return value instanceof Long ? (Long) value : null;
            }
        }

        throw new IllegalArgumentException(
                String.format("파라미터 '%s'를 찾을 수 없습니다.", paramName)
        );
    }
}
