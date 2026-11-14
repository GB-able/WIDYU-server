package com.widyu.album.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/svg+xml",
            "image/heic", "image/heif" // 아이폰 기본 카메라 형식(#1041)
    );
    private static final String SVG = "image/svg+xml";
    private static final int SIGNATURE_BYTES = 12;
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis");
    private static final Set<String> HEIF_BRANDS = Set.of("mif1", "msf1", "heif");
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
        validateCounts(s);

        for (MultipartFile f : files) {
            String ct = contentTypeOrThrow(f);

            validateAllowedType(ct);
            validateSize(ct, f.getSize());
        }
    }

    public void validateMetadata(List<MediaMetadata> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }

        MediaSummary s = summarizeMetadata(files);
        validateCounts(s);

        for (MediaMetadata file : files) {
            if (file.fileSize() <= 0) {
                throw new BusinessException(ErrorCode.FILE_IS_EMPTY, "파일 크기가 올바르지 않습니다.");
            }
            validateAllowedType(file.contentType());
            validateSize(file.contentType(), file.fileSize());
        }
    }

    private MediaSummary summarizeMetadata(List<MediaMetadata> files) {
        int photos = 0;
        int videos = 0;

        for (MediaMetadata file : files) {
            String ct = file.contentType();
            if (ct == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }

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

    private static void validateCounts(MediaSummary s) {
        if (s.total() > MAX_TOTAL || s.photos() > MAX_PHOTO || s.videos() > MAX_VIDEO) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "전체 최대 8개, 사진 최대 8개, 동영상 최대 3개까지 업로드 가능합니다."
            );
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

    /**
     * 서버가 받아 바로 올리는 사진 한 장(복약 인증, 프로필)을 앨범 사진과 같은 허용 목록, 크기 상한으로 검사한다(LLD-0150).
     * 형식은 클라이언트 Content-Type이 아닌 파일 앞부분 시그니처로 정하고, 저장할 Content-Type과 확장자로 돌려준다.
     * SVG는 스크립트를 담을 수 있어 받지 않는다.
     */
    public static ImageFormat validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }

        ImageFormat format = detectImageFormat(readSignature(file));
        if (format == null || SVG.equals(format.contentType()) || !ALLOWED_IMAGES.contains(format.contentType())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        validateSize(format.contentType(), file.getSize());
        return format;
    }

    private static byte[] readSignature(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_BYTES);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private static ImageFormat detectImageFormat(byte[] head) {
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return new ImageFormat("image/jpeg", "jpg");
        }
        if (startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new ImageFormat("image/png", "png");
        }
        if (hasAscii(head, 0, "GIF87a") || hasAscii(head, 0, "GIF89a")) {
            return new ImageFormat("image/gif", "gif");
        }
        if (hasAscii(head, 0, "RIFF") && hasAscii(head, 8, "WEBP")) {
            return new ImageFormat("image/webp", "webp");
        }
        if (hasAscii(head, 0, "BM")) {
            return new ImageFormat("image/bmp", "bmp");
        }
        if (!hasAscii(head, 4, "ftyp") || head.length < SIGNATURE_BYTES) {
            return null;
        }
        String brand = new String(head, 8, 4, StandardCharsets.US_ASCII);
        if (HEIC_BRANDS.contains(brand)) {
            return new ImageFormat("image/heic", "heic");
        }
        if (HEIF_BRANDS.contains(brand)) {
            return new ImageFormat("image/heif", "heif");
        }
        return null;
    }

    private static boolean hasAscii(byte[] head, int offset, String expected) {
        return startsWith(head, offset, expected.chars().toArray());
    }

    private static boolean startsWith(byte[] head, int offset, int... expected) {
        if (head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    public record ImageFormat(String contentType, String extension) {}

    public record MediaSummary(int photos, int videos, int total) {}

    public record MediaMetadata(String contentType, long fileSize) {}
}
