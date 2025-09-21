package com.widyu.domain.album.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record AlbumUploadRequest(
        @Size(max = 2200, message = "게시글 내용은 최대 2,200자까지 입력 가능합니다.")
        String content,
        
        @NotNull(message = "미디어 파일은 필수입니다.")
        @Size(min = 1, max = 13, message = "미디어 파일은 최소 1개, 최대 13개까지 업로드 가능합니다.")
        List<MultipartFile> mediaFiles
) {
    
    public int getPhotoCount() {
        return (int) mediaFiles.stream()
                .filter(file -> file.getContentType() != null && file.getContentType().startsWith("image/"))
                .count();
    }
    
    public int getVideoCount() {
        return (int) mediaFiles.stream()
                .filter(file -> file.getContentType() != null && file.getContentType().startsWith("video/"))
                .count();
    }
    
    public boolean hasValidMediaCount() {
        int photoCount = getPhotoCount();
        int videoCount = getVideoCount();
        
        // 사진 최대 10개, 동영상 최대 3개
        return photoCount <= 10 && videoCount <= 3;
    }
}