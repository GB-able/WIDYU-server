package com.widyu.device.controller;

import com.widyu.device.application.DeviceEventService;
import com.widyu.device.controller.docs.DeviceEventDocs;
import com.widyu.device.dto.response.DeviceEventResponse;
import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.global.util.SecurityUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/device")
public class DeviceEventController implements DeviceEventDocs {

    private final DeviceEventService deviceEventService;
    private final SecurityUtil securityUtil;

    /** 항목마다 앱이 보낸 원문을 그대로 남기려고 본문을 원문 바이트로 받는다(LLD-0129 5절). */
    @Override
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponseTemplate<List<DeviceEventResponse>> ingestEvents(@RequestBody byte[] payload) {
        List<DeviceEventResponse> response =
                deviceEventService.ingest(securityUtil.getCurrentMemberId(), payload);
        return ApiResponseTemplate.ok()
                .code("200")
                .message("기기 이벤트를 받았습니다.")
                .body(response);
    }
}
