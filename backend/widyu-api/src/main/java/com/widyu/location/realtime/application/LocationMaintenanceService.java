package com.widyu.location.realtime.application;

import com.widyu.location.SeniorLocation;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.stay.application.SeniorStayStateService;
import com.widyu.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 위치 저장소 유지관리(LLD-0142): 최신 위치 TTL 연장, 탈퇴, 비활성 회원의 위치와 체류 상태 삭제. → LLD-0059, LLD-0141 */
@Service
@RequiredArgsConstructor
public class LocationMaintenanceService {

    // @RedisHash 저장소가 쓰는 키 모양(keyspace:id)과 같아야 만료 연장이 그 위치에 걸린다.
    private static final String SENIOR_LOCATION_KEY_PREFIX = SeniorLocation.KEYSPACE + ":";
    // 체류 판정 상태는 MySQL(senior_stay_state)이 권위 있는 저장소다(LLD-0141). 이 키는 예전 값을 지울 때만 쓴다.
    private static final String LEGACY_STAY_KEY_PREFIX = "location:stay:";

    private final SeniorLocationRepository seniorLocationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MemberRepository memberRepository;
    private final PlatformTransactionManager transactionManager;
    private final SeniorStayStateService stayStateService;

    /**
     * 4분마다 실행 — SeniorLocation(5분 TTL)의 만료 시간을 늘려 시니어가 정지 중이어도 마지막 위치가 사라지지 않게 한다.
     * 마지막 위치를 남겨 두는 것이지 새 위치로 만드는 것이 아니다. 신선도는 응답의 측정 시각으로 판단한다(LLD-0141).
     * 활성 회원이 아니면 갱신하지 않고 지운다. → LLD-0059
     *
     * <p>위치 본문은 다시 저장하지 않고 만료 시간만 늘린다(LLD-0140). 읽어 둔 위치를 다시 저장하면, 읽은 뒤 위치 수신이
     * 저장한 새 위치를 옛 위치로 덮는다. 키가 이미 없으면(탈퇴 삭제, 만료) 만료 연장은 아무것도 하지 않는다.
     *
     * <p>체류 상태는 MySQL에 있어 만료가 없으므로 활성이 아닌 회원의 행을 여기서 함께 지운다. 활성 회원 조회는 짧은 읽기
     * 트랜잭션에서 끝낸다. 회원 저장소의 선언 쿼리는 스스로 트랜잭션을 열지 않아, 이 범위({@code NOT_SUPPORTED})에서 그냥
     * 부르면 묶인 EntityManager가 메서드 끝까지 커넥션을 쥔 채 회원별 삭제 트랜잭션이 두 번째 커넥션을 기다린다.
     */
    @Scheduled(fixedRate = 240_000)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshLocationTtl() {
        // 최신 위치가 만료된 비활성 회원의 체류 상태 행도 지운다(Redis 키처럼 스스로 만료되지 않는다).
        stayStateService.findInactiveMemberIds().forEach(this::deleteLocation);
        List<SeniorLocation> locations = new ArrayList<>();
        seniorLocationRepository.findAll().forEach(location -> {
            if (location != null) {
                locations.add(location);
            }
        });
        if (locations.isEmpty()) {
            return;
        }
        List<Long> memberIds = locations.stream().map(SeniorLocation::getSeniorId).toList();
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        Set<Long> activeMemberIds = readOnly.execute(status -> memberRepository.findActiveIdsIn(memberIds));
        for (SeniorLocation location : locations) {
            if (!activeMemberIds.contains(location.getSeniorId())) {
                deleteLocation(location.getSeniorId());
                continue;
            }
            redisTemplate.expire(SENIOR_LOCATION_KEY_PREFIX + location.getSeniorId(),
                    SeniorLocation.TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 탈퇴하거나 활성이 아닌 회원의 위치를 지운다: Redis 현재 위치, 이동 경로, 예전 체류 키와 MySQL 체류 상태. → LLD-0059, LLD-0141
     * 체류 상태 삭제는 자기 트랜잭션에서 끝난다(탈퇴 커밋 뒤 리스너에서도 부른다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteLocation(Long memberId) {
        try {
            stayStateService.delete(memberId);
        } finally {
            // DB 삭제가 실패해도 Redis 위치는 지우고, Redis 삭제가 실패해도 DB 행은 이미 지웠다.
            seniorLocationRepository.deleteById(memberId);
            redisTemplate.delete(List.of(LocationProjectionService.trailKey(memberId), LEGACY_STAY_KEY_PREFIX + memberId));
        }
    }
}
