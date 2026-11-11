package com.widyu.device.dto.response;

/** 기기 이벤트 항목별 수신 결과(LLD-0129 3절). 앱은 STORED·DUPLICATE를 받은 항목만 보관분에서 지운다. */
public enum DeviceEventResult {
    STORED,
    DUPLICATE,
    /** 형식 오류. 다시 보내도 같은 결과다. */
    REJECTED
}
