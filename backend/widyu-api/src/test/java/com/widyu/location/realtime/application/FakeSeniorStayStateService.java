package com.widyu.location.realtime.application;

import com.widyu.location.stay.SeniorStayState;
import com.widyu.location.stay.application.SeniorStayStateService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 단위 테스트용 체류 상태 저장소. 행 잠금과 트랜잭션은 통합 테스트(StayStateOutboxAtomicityTest)가 본다. */
class FakeSeniorStayStateService extends SeniorStayStateService {

    final Map<Long, SeniorStayState> states = new ConcurrentHashMap<>();
    /** 활성 회원이 아닌 것으로 볼 회원. 실제 서비스는 회원 표와 조인한다. */
    final java.util.Set<Long> inactiveMemberIds = ConcurrentHashMap.newKeySet();

    FakeSeniorStayStateService() {
        super(null, null);
    }

    @Override
    public Optional<SeniorStayState> find(Long memberId) {
        return Optional.ofNullable(states.get(memberId));
    }

    @Override
    public Optional<SeniorStayState> lockForChange(Long memberId) {
        return find(memberId);
    }

    @Override
    public SeniorStayState create(SeniorStayState state) {
        states.put(state.getMemberId(), state);
        return state;
    }

    @Override
    public List<Long> findInactiveMemberIds() {
        return states.keySet().stream().filter(inactiveMemberIds::contains).toList();
    }

    @Override
    public void delete(Long memberId) {
        states.remove(memberId);
    }
}
