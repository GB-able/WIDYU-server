package com.widyu.admin.dto.request;

import com.widyu.fcm.FcmCategory;

public record AdminFcmTestRequest(
        Long memberId,
        String title,
        String content,
        FcmCategory category
) {}
