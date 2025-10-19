package com.widyu.album.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AlbumMediaPolicy {

    public static final int MAX_TOTAL = 8;
    public static final int MAX_PHOTO = 8;
    public static final int MAX_VIDEO = 3;
    public static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;       // 10MB
    public static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024; // 2GB

    private static final Set<String> ALLOWED_IMAGES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/svg+xml"
    );
    private static final Set<String> ALLOWED_VIDEOS = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/x-matroska",
            "video/webm", "video/x-flv", "video/x-ms-wmv"
    );

    public MediaSummary summarize(List<MultipartFile> files) {
        int photos = 0;
        int videos = 0;

        for (MultipartFile f : files) {
            String ct = contentTypeOrThrow(f);

            if (isImage(ct)) {
                photos++;
                continue;
            }
            if (isVideo(ct)) {
                videos++;
                continue;
            }
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        return new MediaSummary(photos, videos, photos + videos);
    }

    public void validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }

        MediaSummary s = summarize(files);
        if (s.total() > MAX_TOTAL || s.photos() > MAX_PHOTO || s.videos() > MAX_VIDEO) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "전체 최대 8개, 사진 최대 8개, 동영상 최대 3개까지 업로드 가능합니다."
            );
        }

        for (MultipartFile f : files) {
            String ct = contentTypeOrThrow(f);

            validateAllowedType(ct);
            validateSize(ct, f.getSize());
        }
    }

    private static String contentTypeOrThrow(MultipartFile f) {
        String ct = f.getContentType();
        if (ct == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        return ct;
    }

    private static boolean isImage(String ct) {
        return ct.startsWith("image/");
    }

    private static boolean isVideo(String ct) {
        return ct.startsWith("video/");
    }

    private static void validateAllowedType(String ct) {
        if (isImage(ct) && ALLOWED_IMAGES.contains(ct)) return;
        if (isVideo(ct) && ALLOWED_VIDEOS.contains(ct)) return;
        throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
    }

    private static void validateSize(String ct, long size) {
        if (isImage(ct) && size > MAX_PHOTO_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "사진은 최대 10MB까지 업로드 가능합니다.");
        }
        if (isVideo(ct) && size > MAX_VIDEO_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "동영상은 최대 2GB까지 업로드 가능합니다.");
        }
    }

    public record MediaSummary(int photos, int videos, int total) {}
}
