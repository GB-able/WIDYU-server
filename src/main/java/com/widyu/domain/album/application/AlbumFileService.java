package com.widyu.domain.album.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.infrastructure.s3.S3Service;
import com.widyu.infrastructure.video.VideoCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumFileService {

    private final S3Service s3Service;
    private final VideoCompressionService videoCompressionService;

    private static final String ALBUM_PHOTO_PREFIX = "albums/photos";
    private static final String ALBUM_VIDEO_PREFIX = "albums/videos";
    private static final String THUMBNAIL_PREFIX = "albums/thumbnails";
    private static final long MAX_PHOTO_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_VIDEO_SIZE = 2L * 1024 * 1024 * 1024; // 2GB (압축 전 제한)

    public String uploadAlbumPhoto(MultipartFile file, Long memberId) {
        validatePhotoFile(file);
        String directory = String.format("%s/%d", ALBUM_PHOTO_PREFIX, memberId);
        String filePath = s3Service.generateFilePath(directory, file.getOriginalFilename());
        return s3Service.uploadFile(file, filePath);
    }

    public String uploadAlbumVideo(MultipartFile file, Long memberId) {
        validateVideoFile(file);
        
        MultipartFile processedFile = file;
        File tempCompressedFile = null;
        
        try {
            // 압축이 필요한 경우 압축 실행
            if (videoCompressionService.needsCompression(file)) {
                log.info("동영상 압축 시작: fileName={}, originalSize={}MB",
                        file.getOriginalFilename(), file.getSize() / (1024 * 1024));
                
                tempCompressedFile = videoCompressionService.compressVideo(file);
                
                // 압축된 파일을 MultipartFile로 변환
                processedFile = convertFileToMultipartFile(tempCompressedFile, file.getOriginalFilename());
                
                log.info("동영상 압축 완료: fileName={}, compressedSize={}MB", 
                        file.getOriginalFilename(), processedFile.getSize() / (1024 * 1024));
            }
            
            // S3 업로드
            String directory = String.format("%s/%d", ALBUM_VIDEO_PREFIX, memberId);
            String filePath = s3Service.generateFilePath(directory, processedFile.getOriginalFilename());
            return s3Service.uploadFile(processedFile, filePath);
            
        } catch (IOException e) {
            log.error("동영상 처리 실패: fileName={}, error={}", file.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        } finally {
            // 임시 압축 파일 정리
            if (tempCompressedFile != null) {
                videoCompressionService.cleanupTempFile(tempCompressedFile);
            }
        }
    }

    // 썸네일 이미지 업로드 (동영상용)
    public String uploadThumbnail(MultipartFile file, Long memberId) {
        validatePhotoFile(file);
        String directory = String.format("%s/%d", THUMBNAIL_PREFIX, memberId);
        String filePath = s3Service.generateFilePath(directory, file.getOriginalFilename());
        return s3Service.uploadFile(file, filePath);
    }

    public List<String> uploadMediaFiles(List<MultipartFile> mediaFiles, Long memberId) {
        List<String> mediaUrls = new ArrayList<>();

        for (MultipartFile file : mediaFiles) {
            try {
                String contentType = file.getContentType();
                if (contentType == null) {
                    cleanupUploadedFiles(mediaUrls);
                    throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
                }

                String mediaUrl;
                if (contentType.startsWith("image/")) {
                    mediaUrl = uploadAlbumPhoto(file, memberId);
                    mediaUrls.add(mediaUrl);
                    log.debug("이미지 업로드 성공: memberId={}, url={}", memberId, mediaUrl);
                    continue;
                }

                if (contentType.startsWith("video/")) {
                    mediaUrl = uploadAlbumVideo(file, memberId);
                    mediaUrls.add(mediaUrl);
                    log.debug("비디오 업로드 성공: memberId={}, url={}", memberId, mediaUrl);
                    continue;
                }

                // 여기까지 오면 지원하지 않는 타입
                cleanupUploadedFiles(mediaUrls);
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);

            } catch (Exception e) {
                cleanupUploadedFiles(mediaUrls);
                log.error("미디어 파일 업로드 실패: memberId={}, fileName={}, error={}",
                        memberId, file.getOriginalFilename(), e.getMessage());
                throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }

        return mediaUrls;
    }

    private void validatePhotoFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }

        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void validateVideoFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_IS_EMPTY);
        }

        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void cleanupUploadedFiles(List<String> uploadedUrls) {
        for (String url : uploadedUrls) {
            try {
                s3Service.deleteFile(url);
            } catch (Exception e) {
                log.warn("파일 정리 실패: url={}, error={}", url, e.getMessage());
            }
        }
    }

    private MultipartFile convertFileToMultipartFile(File file, String originalFileName) throws IOException {
        String fileName = originalFileName != null ? originalFileName : file.getName();
        String contentType = "video/mp4";
        
        return new MultipartFile() {
            @NotNull
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return fileName;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return file.length() == 0;
            }

            @Override
            public long getSize() {
                return file.length();
            }

            @NotNull
            @Override
            public byte[] getBytes() throws IOException {
                return Files.readAllBytes(file.toPath());
            }

            @NotNull
            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return new FileInputStream(file);
            }

            @Override
            public void transferTo(@NotNull File dest) throws IOException {
                Files.copy(file.toPath(), dest.toPath());
            }
        };
    }
}