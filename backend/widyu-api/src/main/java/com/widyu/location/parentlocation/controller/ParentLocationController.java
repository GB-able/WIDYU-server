package com.widyu.location.parentlocation.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.location.parentlocation.application.ParentLocationService;
import com.widyu.location.parentlocation.controller.docs.ParentLocationDocs;
import com.widyu.location.parentlocation.dto.request.ParentLocationCreateRequest;
import com.widyu.location.parentlocation.dto.request.ParentLocationUpdateRequest;
import com.widyu.location.parentlocation.dto.response.ParentLocationResponse;
import com.widyu.location.parentlocation.dto.response.SeniorWithLocationsResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goals/parent-locations")
public class ParentLocationController implements ParentLocationDocs {

    private final ParentLocationService parentLocationService;

    @PostMapping
    public ApiResponseTemplate<Void> createParentLocation(
            @Valid @RequestBody ParentLocationCreateRequest request
    ) {
        parentLocationService.create(request);
        return ApiResponseTemplate.ok()
                .code("PLO_2001")
                .message("부모님 장소가 등록되었습니다.")
                .build();
    }

    @DeleteMapping("/{memberId}/{parentLocationId}")
    public ApiResponseTemplate<Void> deleteParentLocation(
            @PathVariable Long memberId,
            @PathVariable Long parentLocationId
    ) {
        parentLocationService.delete(memberId, parentLocationId);
        return ApiResponseTemplate.ok()
                .code("PLO_2002")
                .message("부모님 장소가 삭제되었습니다.")
                .build();
    }

    @GetMapping("")
    public ApiResponseTemplate<List<SeniorWithLocationsResponse>> getParentLocations(
    ) {
        List<SeniorWithLocationsResponse> data = parentLocationService.findAllByGuardianId();
        return ApiResponseTemplate.ok()
                .code("PLO_2000")
                .message("부모님 장소 목록이 조회되었습니다.")
                .body(data);
    }

    @PatchMapping("/{parentLocationId}")
    public ApiResponseTemplate<Void> updateParentLocation(
            @PathVariable Long parentLocationId,
            @Valid @RequestBody ParentLocationUpdateRequest request
    ) {
        parentLocationService.update(parentLocationId, request);
        return ApiResponseTemplate.ok()
                .code("PLO_2003")
                .message("부모님 장소가 수정되었습니다.")
                .build();
    }
}
