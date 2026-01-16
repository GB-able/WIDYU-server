package com.widyu.ai.controller.docs;

import com.widyu.ai.dto.request.HeartRateAnomalyRequest;
import com.widyu.ai.dto.response.HeartRateAnomalyResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "AI Test", description = "AI 서버 테스트 API")
public interface AiTestDocs {

    @Operation(
            summary = "심박수 이상치 판별 테스트",
            description = """
                    AI 서버를 통해 심박수 데이터의 이상치를 판별합니다.

                    **기능:**
                    - 15개의 심박수 데이터를 AI 서버로 전송
                    - AI 모델이 정상/비정상 판별
                    - 판별 결과 반환

                    **AI 서버 응답:**
                    - 0: 정상 심박수
                    - 1: 비정상 심박수 (이상치 감지)

                    **입력 데이터:**
                    - 정확히 15개의 심박수 값 필요
                    - 각 값은 양의 정수

                    **예시:**
                    - 정상 패턴: [82,85,83,84,80,82,85,88,87,85,83,82,80,77,79]
                    - 비정상 패턴: [82,85,83,90,120,100,84,88,92,95,100,85,80,77,125]

                    **참고:**
                    - Docker Compose로 배포된 widyu-ai 서버와 내부 통신
                    - 타임아웃: 연결 5초, 읽기 10초
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판별 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (데이터 개수 오류 등)"),
            @ApiResponse(responseCode = "500", description = "AI 서버 통신 실패")
    })
    ApiResponseTemplate<HeartRateAnomalyResponse> detectHeartRateAnomaly(
            @Valid @RequestBody HeartRateAnomalyRequest request
    );
}
