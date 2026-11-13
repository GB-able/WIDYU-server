package com.widyu.location.stay.application;

import com.widyu.location.stay.SeniorStayState;
import com.widyu.location.stay.repository.SeniorStayStateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체류와 안전구역 판정 상태의 읽기, 잠금, 삭제(LLD-0141). 판정 자체는 위치 처리 트랜잭션 안에서 {@code LocationJudgeService}가
 * 하고, 이 서비스는 그 트랜잭션에 참여해 행을 읽거나 잠근다.
 */
@Service
@RequiredArgsConstructor
public class SeniorStayStateService {

    private final SeniorStayStateRepository repository;
    private final EntityManager entityManager;

    /** 잠그지 않고 읽는다. 같은 자리 위치는 이것으로 판정이 끝나 행을 쓰지도 잠그지도 않는다. */
    @Transactional(readOnly = true)
    public Optional<SeniorStayState> find(Long memberId) {
        return repository.findById(memberId);
    }

    /**
     * 상태를 바꾸기 전에 행을 잠그고 최신 값으로 다시 읽는다. 이미 읽어 둔 엔티티가 있으면 그 값을 DB에서 새로 고친다
     * (잠금 조회만으로는 영속성 컨텍스트에 있던 옛 값이 그대로 남는다). 있는 PK의 잠금 조회는 그 행만 잠근다.
     * 위치 처리 트랜잭션 안에서만 부른다.
     *
     * <p>행이 없으면 잠그지 않고 비어 있음을 돌려준다. REPEATABLE READ에서 없는 PK를 잠금 조회하면 그 자리의 틈(gap)이
     * 잠기고, 첫 행을 함께 만드는 다른 회원들의 INSERT가 서로의 틈 잠금에 막혀 교착(1213)한다. 실제 MySQL에서 회원 20명이
     * 처음 위치를 동시에 보낼 때 재현됐다(H2는 틈 잠금이 없어 드러나지 않는다). 같은 회원의 첫 행을 두 트랜잭션이 함께
     * 만들면 PK가 하나만 남기고 진 쪽은 롤백돼 재전송으로 회복한다(LLD-0141 5.3).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SeniorStayState> lockForChange(Long memberId) {
        SeniorStayState current = entityManager.find(SeniorStayState.class, memberId);
        if (current == null) {
            return Optional.empty();
        }
        entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(current);
    }

    /** 새 행을 INSERT한다. 같은 PK가 이미 있으면 커밋 때 무결성 오류로 트랜잭션 전체(상태, 알림)가 롤백된다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public SeniorStayState create(SeniorStayState state) {
        entityManager.persist(state);
        return state;
    }

    /** 활성이 아닌 회원의 상태 행. 잠그지 않고 읽어 정리 대상만 고른다(일괄 DELETE는 훑은 행을 모두 잠가 위치 처리와 겹친다). */
    @Transactional(readOnly = true)
    public List<Long> findInactiveMemberIds() {
        return repository.findInactiveMemberIds();
    }

    /**
     * 탈퇴, 비활성 회원 정리 때 지운다. 탈퇴 커밋 뒤 리스너와 트랜잭션 없는 스케줄러에서 부르므로 자기 트랜잭션에서 끝낸다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(Long memberId) {
        repository.deleteByMemberId(memberId);
    }
}
