package com.widyu.domain.album.api;

import com.widyu.domain.album.application.AlbumCalendarService;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/album/calendar")
public class AlbumCalendarController {

    private final AlbumCalendarService albumCalendarService;

    @GetMapping
    public ApiResponseTemplate<List<Integer>> getDaysWithEvents(
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("OK")
                .body(albumCalendarService.getDaysWithEvents(year, month));
    }
}
