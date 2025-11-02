package com.widyu.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 보호자가 시니어의 리소스에 접근할 때 가족 관계를 검증하는 어노테이션
 *
 * <p>사용 예시:
 * <pre>
 * {@code
 * @GetMapping("/walks/detail")
 * @ValidateFamilyAccess(memberIdParam = "memberId")
 * public ResponseEntity<?> getWalkDetail(@RequestParam Long memberId) {
 *     // ...
 * }
 * }
 * </pre>
 *
 * <p>동작 방식:
 * <ul>
 *   <li>memberId가 null → 본인의 리소스 접근 (검증 불필요)</li>
 *   <li>memberId가 본인 ID → 본인의 리소스 접근 (검증 불필요)</li>
 *   <li>memberId가 다른 사람 ID → 보호자인지 확인 + 가족 관계 검증</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateFamilyAccess {

    /**
     * 검증할 memberId 파라미터의 이름
     * 컨트롤러 메서드의 파라미터 이름과 일치해야 합니다.
     *
     * @return 파라미터 이름
     */
    String memberIdParam() default "memberId";
}
