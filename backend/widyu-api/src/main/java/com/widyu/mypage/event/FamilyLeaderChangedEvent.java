package com.widyu.mypage.event;

/** 방장 변경. 이전 방장은 변경을 실행한 사람이라 R02 수신자에서 뺀다. */
public record FamilyLeaderChangedEvent(Long familyId, Long previousLeaderId, Long newLeaderId) {
    public static FamilyLeaderChangedEvent of(Long familyId, Long previousLeaderId, Long newLeaderId) {
        return new FamilyLeaderChangedEvent(familyId, previousLeaderId, newLeaderId);
    }
}
