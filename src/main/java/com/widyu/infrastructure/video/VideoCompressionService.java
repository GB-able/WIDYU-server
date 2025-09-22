package com.widyu.infrastructure.video;

import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;

public interface VideoCompressionService {
    
    /**
     * 동영상을 500MB 이하로 압축
     * @param inputFile 원본 동영상 파일
     * @return 압축된 동영상 파일
     */
    File compressVideo(MultipartFile inputFile) throws IOException;
    
    /**
     * 동영상이 압축이 필요한지 확인
     * @param file 동영상 파일
     * @return 압축 필요 여부
     */
    boolean needsCompression(MultipartFile file);
    
    /**
     * 임시 파일 정리
     * @param file 삭제할 파일
     */
    void cleanupTempFile(File file);
}