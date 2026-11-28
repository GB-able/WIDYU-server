package com.widyu.auth.event;

import java.util.List;

/**
 * 어르신 본인 탈퇴. 가족 연결을 끊고 이름을 가리기 전에 잡아 둔 값이다(LLD-0186).
 * 같은 트랜잭션에서 보호자 알림을 접수하고, 발송은 커밋 뒤 outbox가 한다.
 */
public record SeniorWithdrawnEvent(Long seniorId, String seniorName, List<Long> guardianIds) {
    public static SeniorWithdrawnEvent of(Long seniorId, String seniorName, List<Long> guardianIds) {
        return new SeniorWithdrawnEvent(seniorId, seniorName, List.copyOf(guardianIds));
    }
}
