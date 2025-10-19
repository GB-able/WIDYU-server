package com.widyu.global.infrastructure.video;

import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;

public interface VideoCompressionService {
    boolean needsCompression(MultipartFile file);
    File compressVideo(MultipartFile file) throws IOException;
    File generateThumbnail(MultipartFile file) throws IOException;
    int extractDuration(MultipartFile file) throws IOException;
    void cleanupTempFile(File file);
}