package com.widyu.global.retry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/**
 * 시니어 포인트 잔액(@Version) 낙관적 락 충돌 시 트랜잭션을 새로 열어 재시도한다.
 *
 * <p>재시도 정책: 최대 3회, 50ms에서 시작해 2배씩 증가하는 백오프.
 * 재시도가 모두 실패하면 {@link ObjectOptimisticLockingFailureException}가 그대로 전파되어
 * GlobalExceptionHandler가 409(POINT_CONCURRENT_UPDATE)로 응답한다.
 *
 * <p>재시도가 새 트랜잭션에서 최신 상태를 다시 읽도록, 이 애노테이션은 프록시를 거쳐
 * 호출되는 {@code @Transactional} 메서드(다른 빈에서 진입하는 public 메서드)에만 적용한다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2)
)
public @interface RetryOnPointConflict {
}
