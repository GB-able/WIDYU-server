package com.widyu.auth.controller.docs;

import com.widyu.auth.dto.request.SeniorSignInRequest;
import com.widyu.auth.dto.request.SeniorSignUpRequest;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Auth - Senior", description = "시니어 회원 인증 API")
public interface SeniorAuthDocs {

    @Operation(
            summary = "시니어 일괄 회원가입",
            description = "보호자가 시니어를 일괄 등록합니다. 각 시니어는 이름, 생년월일, 전화번호, 주소, 초대코드를 저장하고 초대코드로 로그인할 수 있습니다.\n\n" +
                    "- 주소는 도로명주소 검색 API(`GET /api/v1/goals/address-search`)로 조회한 값을 사용하세요.\n" +
                    "- 초대코드는 7자리 숫자로 구성되며, 시니어 로그인 시 사용됩니다.\n" +
                    "- 위도(`latitude`)와 경도(`longitude`)는 선택입니다. 함께 보내면 서버의 주소 좌표 변환 대신 이 값으로 집(HOME) 안심구역을 만듭니다. 하나만 보내거나 범위를 벗어나면 400입니다.\n" +
                    "- 좌표를 보내지 않았는데 지오코딩에 실패(유효하지 않은 주소)하면 400 오류가 반환됩니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    [
                                      {
                                        "name": "김부모",
                                        "birthDate": "1955-03-15",
                                        "phoneNumber": "01012345678",
                                        "address": "서울특별시 마포구 성암로 301",
                                        "inviteCode": "1234567",
                                        "latitude": 37.5796,
                                        "longitude": 126.8924
                                      }
                                    ]
                                    """)
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "시니어 일괄 회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (빈 리스트, 유효하지 않은 주소, 요청 안 전화번호 중복 등) 또는 AUTH_4021: 다른 회원이 이미 쓰는 전화번호"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "보호자(GUARDIAN) 타입 회원만 시니어 등록 가능")
    })
    ApiResponseTemplate<Void> seniorSignUpBulk(@RequestBody @Valid List<SeniorSignUpRequest> requests);

    @Operation(
            summary = "시니어 로그인",
            description = "초대코드와 전화번호로 시니어 회원이 로그인합니다.\n\n" +
                    "- 같은 전화번호나 같은 IP에서 실패가 쌓이면 보호자 로컬 로그인과 같은 한도로 일정 시간 거부합니다(429)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "시니어 로그인 성공"),
            @ApiResponse(responseCode = "404", description = "초대코드 또는 전화번호가 일치하지 않음"),
            @ApiResponse(responseCode = "409", description = "MEMBER_4090: 같은 초대코드와 전화번호의 시니어가 여러 명이라 본인을 특정할 수 없음"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "429", description = "AUTH_4290: 전화번호/IP 실패 및 진행 중 요청 한도. Retry-After 초 이후 재시도",
                    headers = @Header(name = "Retry-After", schema = @Schema(type = "integer"))),
            @ApiResponse(responseCode = "503", description = "AUTH_5030: 인증 제한 저장소 장애")
    })
    ApiResponseTemplate<TokenPairResponse> seniorSignIn(@RequestBody @Valid SeniorSignInRequest request);
}
