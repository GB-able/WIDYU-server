package com.widyu.location.realtime.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.widyu.location.raw.application.LocationFixService;
import jakarta.validation.Validator;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 단위 테스트에서 수신 조율자를 조립한다. 판정 빈은 실제 트랜잭션 인터셉터로 감싸 {@code @Transactional}이 붙은 메서드의
 * 시작, 커밋, 롤백이 대역 트랜잭션 관리자에 그대로 찍히게 한다(어노테이션이 빠지면 커밋 순서 테스트가 깨진다).
 * 실제 프록시와 커넥션 반납은 통합 테스트(LocationIngestTransactionBoundaryTest)가 본다.
 */
final class LocationServiceFixture {

    private LocationServiceFixture() {
    }

    static LocationIngestService ingest(Validator validator, LocationFixService locationFixService,
                                        MemberLocationSequencer sequencer, LocationJudgeService judgeService,
                                        LocationProjectionService projectionService,
                                        PlatformTransactionManager transactionManager) {
        return new LocationIngestService(validator, locationFixService, sequencer,
                transactional(judgeService, transactionManager), projectionService);
    }

    @SuppressWarnings("unchecked")
    private static <T> T transactional(T target, PlatformTransactionManager transactionManager) {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (T) proxyFactory.getProxy();
    }
}
