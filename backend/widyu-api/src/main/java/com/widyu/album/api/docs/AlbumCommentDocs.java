package com.widyu.album.api.docs;

import com.widyu.album.dto.request.AlbumCommentCreateRequest;
import com.widyu.album.dto.request.AlbumCommentUpdateRequest;
import com.widyu.album.dto.response.AlbumCommentResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Album-Comment", description = "앨범 댓글 관리 API - 생성, 수정, 삭제")
public interface AlbumCommentDocs {

    @Operation(
            summary = "댓글 작성", 
            description = "앨범에 댓글 또는 대댓글을 작성합니다.",
            requestBody = @RequestBody(
                    description = "댓글 작성 요청",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "일반 댓글",
                                            summary = "새로운 댓글 작성",
                                            description = "앨범에 새로운 댓글을 작성합니다.",
                                            value = """
                                                    {
                                                      "content": "멋진 사진이네요! 어디서 찍으신 건가요?"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "대댓글",
                                            summary = "대댓글 작성",
                                            description = "기존 댓글에 대한 대댓글을 작성합니다.",
                                            value = """
                                                    {
                                                      "content": "저도 궁금해요! 정말 예쁜 곳이네요",
                                                      "parentCommentId": 123
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (기존 댓글 불일치, 깊이 초과)"),
            @ApiResponse(responseCode = "404", description = "앨범 또는 댓글을 찾을 수 없음")
    })
    ApiResponseTemplate<AlbumCommentResponse> createComment(
            @Parameter(description = "앨범 ID", required = true) Long albumId,
            AlbumCommentCreateRequest request
    );

    @Operation(summary = "댓글 수정", description = "내가 작성한 댓글의 내용을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "403", description = "본인의 댓글만 수정 가능"),
            @ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음")
    })
    ApiResponseTemplate<AlbumCommentResponse> updateComment(
            @Parameter(description = "댓글 ID", required = true) Long commentId,
            AlbumCommentUpdateRequest request
    );

    @Operation(summary = "댓글 삭제", description = "내가 작성한 댓글을 삭제합니다. 댓글 삭제 시 대댓글도 함께 삭제됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "댓글 삭제 성공"),
            @ApiResponse(responseCode = "403", description = "본인의 댓글만 삭제 가능"),
            @ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음")
    })
    ApiResponseTemplate<Void> deleteComment(
            @Parameter(description = "댓글 ID", required = true) Long commentId
    );
}
