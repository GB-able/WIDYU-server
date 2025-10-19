package com.widyu.fcm.api.dto.response;

import com.widyu.fcm.FcmCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FcmCategoryResponse {
    private String label;
    private String name;
    private long count;

    public static FcmCategoryResponse of(FcmCategory category, long count) {
        return FcmCategoryResponse.builder()
                .label(category.name())
                .name(getCategoryName(category))
                .count(count)
                .build();
    }

    private static String getCategoryName(FcmCategory category) {
        return switch (category) {
            case ALL -> "전체";
            case ALBUM -> "앨범";
        };
    }
}