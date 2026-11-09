package com.widyu.device.controller.docs;

import com.widyu.device.dto.request.DeviceEventRequest;
import com.widyu.device.dto.response.DeviceEventResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Device Event", description = "실험 모드 기기 상태 바뀜 수신 API")
public interface DeviceEventDocs {

    @Operation(
            summary = "기기 상태 바뀜 묶음 저장",
            description = """
                    실험 모드에서 기기 상태가 바뀐 시각을 묶음으로 저장합니다(LLD-0129).
                    회원은 액세스 토큰에서 확정하며 요청 본문으로 받지 않습니다. 본문은 **이벤트 객체의 JSON 배열**(1~500개)입니다.

                    **항목 필드**: `event_id`(앱이 만든 고유값, UUID 권장, 멱등 키), `device_id`(상태가 바뀐 기기),
                    `source`(`watch`·`phone`), `kind`(종류), `occurred_at_ms`(바뀐 시각, 그 기기의 벽시계 epoch ms),
                    `session_id`(선택), `attributes`(선택, 종류별 값을 담은 JSON 객체). 한 항목 원문은 4,096바이트 이하입니다.

                    **종류(`kind`)**: 대문자 스네이크 문자열입니다. 확정 값은
                    `CHARGING_STARTED`·`CHARGING_STOPPED`, `WEAR_CHANGED`(`on_body` 앱 판정값, `sensor_raw` 착용 센서 원래 값),
                    `PHONE_DISCONNECTED`·`PHONE_RECONNECTED`, `POWER_SAVE_CHANGED`(`enabled`), `BRIGHTNESS_CHANGED`(`level`),
                    `SCREEN_ON`·`SCREEN_OFF`, `SENT_TO_PHONE`(`batch_count`, `bytes`), `NETWORK_CHANGED`(`type`: wifi·mobile·none),
                    `LOCATION_STOPPED`·`LOCATION_RESTARTED`(`reason`)입니다. 예약 값은 `UNLOCKED`, `DOZE_CHANGED`(`idle`),
                    `BATTERY_STATUS`(`temperature_c`, `voltage_mv`), `APP_STARTED`·`APP_EXITED`(`reason`), `GNSS_STATUS`(`satellites`)입니다.
                    **목록에 없는 종류도 64자 이하면 저장합니다.** `attributes`의 키는 서버가 검증하지 않습니다.

                    **결과**: 요청 순서 그대로 항목마다 `STORED`·`DUPLICATE`·`REJECTED`를 돌려줍니다.
                    같은 `(device_id, event_id)`가 이미 있으면 `DUPLICATE`입니다. 형식이 틀린 항목은 그 항목만 `REJECTED`이고
                    나머지는 저장됩니다. `event_id`를 읽지 못한 `REJECTED`는 `eventId`가 null입니다.

                    **재전송 규칙**: 바뀐 시각에 바로 기록하되 전송은 기존 송수신 주기에 얹어도 됩니다.
                    끊긴 동안 쌓인 기록은 앱이 보관했다가 다시 보내고, **항목별 `STORED`·`DUPLICATE`를 받은 뒤에만 지웁니다.**
                    `REJECTED`는 다시 보내도 같은 결과이므로 재전송하지 않습니다. 요청 전체가 실패(네트워크·5xx)하면 같은 묶음을
                    다시 보내면 되고, 이미 저장된 항목은 `DUPLICATE`가 됩니다. 서버는 순서와 지연을 가정하지 않습니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DeviceEventRequest.class)),
                            examples = @ExampleObject(
                                    name = "착용 바뀜과 폰 인터넷 끊김",
                                    value = """
                                            [
                                              {
                                                "event_id": "0b6f2c1e-6a4d-4a51-9a47-5b0b8f7c1d20",
                                                "device_id": "gw-3f2a",
                                                "source": "watch",
                                                "kind": "WEAR_CHANGED",
                                                "occurred_at_ms": 1762650000123,
                                                "session_id": "s-20261109-01",
                                                "attributes": { "on_body": false, "sensor_raw": 0 }
                                              },
                                              {
                                                "event_id": "7d1e9a40-2c55-4f7e-8b61-0f3c2a9e4b11",
                                                "device_id": "ph-9c1",
                                                "source": "phone",
                                                "kind": "NETWORK_CHANGED",
                                                "occurred_at_ms": 1762650001500,
                                                "attributes": { "type": "none" }
                                              }
                                            ]
                                            """
                            )
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "수신 완료 (항목별 결과)",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "200",
                                      "message": "기기 이벤트를 받았습니다.",
                                      "data": [
                                        { "eventId": "0b6f2c1e-6a4d-4a51-9a47-5b0b8f7c1d20", "result": "STORED" },
                                        { "eventId": "7d1e9a40-2c55-4f7e-8b61-0f3c2a9e4b11", "result": "DUPLICATE" },
                                        { "eventId": null, "result": "REJECTED" }
                                      ]
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "본문이 JSON 배열이 아니거나 문법 오류, 0개 또는 500개 초과",
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "DEVICE_4001",
                                      "message": "기기 이벤트 묶음 형식이 올바르지 않습니다.",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "MEMBER_4041",
                                      "message": "회원을 찾을 수 없습니다.",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<List<DeviceEventResponse>> ingestEvents(byte[] payload);
}
