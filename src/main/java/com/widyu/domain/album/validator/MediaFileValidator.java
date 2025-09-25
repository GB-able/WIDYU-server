package com.widyu.domain.album.validator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 미디어 파일 유효성 검증 전담 클래스
 */
@Component
public class MediaFileValidator {
    
    private static final int MAX_TOTAL_FILES = 8;
    private static final int MAX_PHOTO_FILES = 8;
    private static final int MAX_VIDEO_FILES = 3;
    
    public int getPhotoCount(List<MultipartFile> mediaFiles) {
        return (int) mediaFiles.stream()
                .filter(file -> file.getContentType() != null && file.getContentType().startsWith("image/"))
                .count();
    }
    
    public int getVideoCount(List<MultipartFile> mediaFiles) {
        return (int) mediaFiles.stream()
                .filter(file -> file.getContentType() != null && file.getContentType().startsWith("video/"))
                .count();
    }
    
    public boolean hasValidMediaCount(List<MultipartFile> mediaFiles) {
        int photoCount = getPhotoCount(mediaFiles);
        int videoCount = getVideoCount(mediaFiles);
        int totalCount = photoCount + videoCount;
        
        return totalCount <= MAX_TOTAL_FILES && 
               photoCount <= MAX_PHOTO_FILES && 
               videoCount <= MAX_VIDEO_FILES;
    }
    
    public String getValidationMessage(List<MultipartFile> mediaFiles) {
        int photoCount = getPhotoCount(mediaFiles);
        int videoCount = getVideoCount(mediaFiles);
        int totalCount = photoCount + videoCount;
        
        if (totalCount > MAX_TOTAL_FILES) {
            return String.format("전체 파일은 최대 %d개까지 가능합니다. (현재: %d개)", MAX_TOTAL_FILES, totalCount);
        }
        if (photoCount > MAX_PHOTO_FILES) {
            return String.format("사진은 최대 %d개까지 가능합니다. (현재: %d개)", MAX_PHOTO_FILES, photoCount);
        }
        if (videoCount > MAX_VIDEO_FILES) {
            return String.format("동영상은 최대 %d개까지 가능합니다. (현재: %d개)", MAX_VIDEO_FILES, videoCount);
        }
        return null;
    }
}