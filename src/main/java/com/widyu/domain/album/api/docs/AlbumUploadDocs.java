package com.widyu.domain.album.api.docs;

import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Album Upload", description = "앨범 업로드 API")
public interface AlbumUploadDocs {

    @Operation(
            summary = "앨범 업로드",
            description = """
                    새로운 앨범을 업로드합니다.
                    
                    **업로드 제한사항:**
                    - 전체 미디어: 최대 8개 (사진 + 동영상 합계)
                    - 사진: 최대 8개 (각각 최대 10MB)
                    - 동영상: 최대 3개 (각각 최대 500MB, 3분 이내)
                    - 게시글 내용: 최대 2,200자 (공백 포함)
                    
                    **지원 파일 형식:**
                    - 사진: JPG, JPEG, PNG, GIF, WEBP, BMP, SVG
                    - 동영상: MP4, MOV, AVI, MKV, WEBM, FLV, WMV
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "앨범 업로드 성공",
                    content = @Content(
                            schema = @Schema(implementation = AlbumUploadResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 응답",
                                    value = """
                                            {
                                              "code": "ALBM_2001",
                                              "message": "앨범 업로드가 완료되었습니다.",
                                              "data": {
                                                "albumId": 123,
                                                "content": "가족 여행 사진입니다!",
                                                "mediaUrls": [
                                                  "https://s3.bucket.com/albums/photos/1/20241221_143000_abcd1234.jpg",
                                                  "https://s3.bucket.com/albums/videos/1/20241221_143001_efgh5678.mp4"
                                                ],
                                                "mediaCount": 2,
                                                "photoCount": 1,
                                                "videoCount": 1,
                                                "primaryMediaType": "VIDEO",
                                                "authorName": "홍길동",
                                                "createdAt": "2024-12-21T14:30:00"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (파일 크기/개수 초과, 지원하지 않는 파일 형식 등)",
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "파일 개수 초과",
                                            value = """
                                                    {
                                                      "code": "REQ_4000",
                                                      "message": "전체 최대 8개, 사진 최대 8개, 동영상 최대 3개까지 업로드 가능합니다.",
                                                      "data": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "파일 크기 초과",
                                            value = """
                                                    {
                                                      "code": "FILE_4001",
                                                      "message": "파일 크기가 허용 범위를 초과했습니다.",
                                                      "data": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "지원하지 않는 파일 형식",
                                            value = """
                                                    {
                                                      "code": "FILE_4002",
                                                      "message": "지원하지 않는 파일 형식입니다.",
                                                      "data": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "인증 실패",
                                    value = """
                                            {
                                              "code": "AUTH_4010",
                                              "message": "인증이 필요합니다.",
                                              "data": null
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "파일 업로드 실패",
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "서버 오류",
                                    value = """
                                            {
                                              "code": "FILE_5000",
                                              "message": "파일 업로드에 실패했습니다.",
                                              "data": null
                                            }
                                            """
                            )
                    )
            )
    })
    ApiResponseTemplate<AlbumUploadResponse> uploadAlbum(@Valid AlbumUploadRequest request);
}