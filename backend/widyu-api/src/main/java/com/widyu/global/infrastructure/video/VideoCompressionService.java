package com.widyu.global.infrastructure.video;

import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public interface VideoCompressionService {
    boolean needsCompression(MultipartFile file);
    File compressVideo(MultipartFile file) throws IOException;
    File generateThumbnail(File inputFile, double durationSeconds) throws IOException;
    int extractDuration(File inputFile) throws IOException;

    /** 최대 길이를 넘는 영상이면 앞부분만 남긴 새 임시 파일을, 넘지 않으면 빈 값을 돌려준다. */
    Optional<File> trimToMaxDuration(File inputFile) throws IOException;

    void cleanupTempFile(File file);
}