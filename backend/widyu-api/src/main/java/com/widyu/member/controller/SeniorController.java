package com.widyu.member.controller;

import com.widyu.album.dto.response.UnlockedAlbumIdsResponse;
import com.widyu.member.controller.docs.SeniorDocs;
import com.widyu.member.application.SeniorProfileService;
import com.widyu.member.dto.response.SeniorPointsResponse;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/senior")
public class SeniorController implements SeniorDocs {

    private final SeniorProfileService seniorProfileService;

    @Override
    @GetMapping("/points")
    public ApiResponseTemplate<SeniorPointsResponse> getLeftPoints() {
        SeniorPointsResponse response = seniorProfileService.getLeftPoints();
        return ApiResponseTemplate.ok()
                .code("SENIOR_2001")
                .message("시니어 포인트 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/unlocked-albums")
    public ApiResponseTemplate<UnlockedAlbumIdsResponse> getUnlockedAlbums() {
        UnlockedAlbumIdsResponse response = seniorProfileService.getUnlockedAlbums();
        return ApiResponseTemplate.ok()
                .code("SENIOR_2002")
                .message("해금된 앨범 ID 조회 성공")
                .body(response);
    }
}
