package com.widyu.admin;

public enum AdminAction {
    ADMIN_LOGIN,
    MEMBER_STATUS_CHANGE,
    FCM_TEST_SEND,
    COLLECTION_RUN_OPEN,
    COLLECTION_RUN_CLOSE,
    STUDY_PARTICIPATION_REGISTER,
    // 값이 운영 DB에 남아 있으므로 이름을 바꾸지 않는다. 보관 계획 수정에 그대로 쓴다.
    STUDY_PARTICIPATION_PERIOD_CHANGE,
    STUDY_PARTICIPATION_WITHDRAW,
    STUDY_PARTICIPATION_DELETION_PROCESSED,
    // 어르신 탈퇴 요청의 운영자 처리 기록. 본인 즉시 탈퇴(LLD-0186)로 더 쓰지 않지만 옛 감사 로그 행을 읽도록 값만 둔다.
    MEMBER_WITHDRAWAL_REQUEST_PROCESSED,
    MEMBER_WITHDRAWAL_REQUEST_CANCELLED,
}
