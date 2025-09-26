package com.widyu.domain.member.api;

import com.widyu.domain.album.dto.response.UnlockedAlbumIdsResponse;
import com.widyu.domain.member.api.docs.ParentDocs;
import com.widyu.domain.member.application.ParentProfileService;
import com.widyu.domain.member.dto.response.ParentPointsResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/parent")
public class ParentController implements ParentDocs {

    private final ParentProfileService parentProfileService;

    @Override
    @GetMapping("/points")
    public ApiResponseTemplate<ParentPointsResponse> getLeftPoints() {
        ParentPointsResponse response = parentProfileService.getLeftPoints();
        return ApiResponseTemplate.ok()
                .code("PARENT_2001")
                .message("부모 포인트 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/unlocked-albums")
    public ApiResponseTemplate<UnlockedAlbumIdsResponse> getUnlockedAlbums() {
        UnlockedAlbumIdsResponse response = parentProfileService.getUnlockedAlbums();
        return ApiResponseTemplate.ok()
                .code("PARENT_2002")
                .message("해금된 앨범 ID 조회 성공")
                .body(response);
    }
}