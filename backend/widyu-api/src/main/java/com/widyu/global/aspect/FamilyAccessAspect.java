package com.widyu.global.aspect;

import com.widyu.global.annotation.ValidateFamilyAccess;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
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
    private final FamilyMembershipRepository familyMembershipRepository;

    @Before("@annotation(validateFamilyAccess)")
    public void validateFamilyAccess(JoinPoint joinPoint, ValidateFamilyAccess validateFamilyAccess) {
        Member currentMember = memberUtil.getCurrentMember();
        String memberIdParamName = validateFamilyAccess.memberIdParam();

        Long targetMemberId = extractMemberId(joinPoint, memberIdParamName);

        if (targetMemberId == null || targetMemberId.equals(currentMember.getId())) {
            return;
        }

        if (currentMember.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "보호자만 다른 사용자의 리소스에 접근할 수 있습니다.");
        }

        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 사용자입니다."));

        if (targetMember.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어의 리소스만 접근할 수 있습니다.");
        }

        if (targetMember.getSeniorProfile() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어 프로필이 없습니다.");
        }

        boolean isFamily = familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(
                currentMember.getId(),
                targetMember.getSeniorProfile().getId()
        );

        if (!isFamily) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "가족으로 연결된 시니어만 접근할 수 있습니다.");
        }

        log.debug("가족 관계 검증 성공: guardianId={}, seniorId={}",
                currentMember.getId(), targetMemberId);
    }

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
