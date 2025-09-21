package com.widyu.infrastructure.s3;

import org.springframework.web.multipart.MultipartFile;

public interface S3Service {
    
    /**
     * 파일을 S3에 업로드하고 URL 반환
     */
    String uploadFile(MultipartFile file, String filePath);
    
    /**
     * S3에서 파일 삭제
     */
    void deleteFile(String fileUrl);
    
    /**
     * 파일 경로 생성
     */
    String generateFilePath(String directory, String fileName);
}