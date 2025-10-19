package com.widyu.global.infrastructure.video;

import com.widyu.global.properties.FFmpegProperties;
import com.widyu.global.properties.FFprobeProperties;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FFmpegVideoCompressionService implements VideoCompressionService {

    private final FFmpegProperties ffmpegProperties;
    private final FFprobeProperties ffprobeProperties;
    
    private static final long TARGET_SIZE_BYTES = 500 * 1024 * 1024; // 500MB
    private static final int MAX_DURATION_SECONDS = 180; // 3분
    
    @Override
    public File compressVideo(MultipartFile inputFile) throws IOException {
        File tempInputFile = null;
        File tempOutputFile = null;
        
        try {
            // 1. 임시 파일 생성
            tempInputFile = createTempFile(inputFile, "input");
            tempOutputFile = createTempOutputFile();
            
            // 2. FFmpeg 객체 생성
            FFmpeg ffmpeg = new FFmpeg(ffmpegProperties.path());
            FFprobe ffprobe = new FFprobe(ffprobeProperties.path());
            
            // 3. 원본 동영상 정보 분석
            FFmpegProbeResult probeResult = ffprobe.probe(tempInputFile.getAbsolutePath());
            FFmpegStream videoStream = probeResult.getStreams().stream()
                    .filter(stream -> stream.codec_type == FFmpegStream.CodecType.VIDEO)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));
            
            // 4. 반복적 압축으로 500MB 이하 보장
            File finalOutputFile = compressUntilTargetSize(tempInputFile, probeResult, ffmpeg, ffprobe, inputFile.getOriginalFilename());
            
            log.info("동영상 압축 완료: 원본={}MB, 최종={}MB", 
                    inputFile.getSize() / (1024 * 1024), 
                    finalOutputFile.length() / (1024 * 1024));
            
            return finalOutputFile;
            
        } catch (Exception e) {
            // 실패 시 임시 파일 정리
            if (tempOutputFile != null && tempOutputFile.exists()) {
                tempOutputFile.delete();
            }
            log.error("동영상 압축 실패: fileName={}, error={}", inputFile.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 압축 중 오류가 발생했습니다.");
        } finally {
            // 입력 임시 파일 정리
            if (tempInputFile != null && tempInputFile.exists()) {
                tempInputFile.delete();
            }
        }
    }
    
    @Override
    public boolean needsCompression(MultipartFile file) {
        return file.getSize() > TARGET_SIZE_BYTES;
    }
    
    @Override
    public void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                log.warn("임시 파일 삭제 실패: {}", file.getAbsolutePath());
            }
        }
    }

    @Override
    public File generateThumbnail(MultipartFile inputFile) throws IOException {
        File tempInputFile = null;
        File tempThumbnailFile = null;
        
        try {
            // 1. 임시 파일 생성
            tempInputFile = createTempFile(inputFile, "thumbnail_input");
            tempThumbnailFile = createTempThumbnailFile();
            
            // 2. FFmpeg 객체 생성
            FFmpeg ffmpeg = new FFmpeg(ffmpegProperties.path());
            FFprobe ffprobe = new FFprobe(ffprobeProperties.path());
            
            // 3. 썸네일 생성 (1초 지점에서 스크린샷)
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(tempInputFile.getAbsolutePath())
                    .overrideOutputFiles(true)
                    .addOutput(tempThumbnailFile.getAbsolutePath())
                    .setFormat("image2")
                    .setVideoCodec("mjpeg")
                    .setVideoFrameRate(1) // 1프레임만
                    .setVideoResolution(640, 480) // 썸네일 크기
                    .addExtraArgs("-ss", "1") // 1초 지점
                    .addExtraArgs("-vframes", "1") // 1프레임만
                    .addExtraArgs("-q:v", "2") // 고품질
                    .done();
                    
            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
            executor.createJob(builder).run();
            
            log.info("썸네일 생성 완료: 파일명={}, 크기={}KB", 
                    inputFile.getOriginalFilename(), tempThumbnailFile.length() / 1024);
            
            return tempThumbnailFile;
            
        } catch (Exception e) {
            // 실패 시 임시 파일 정리
            if (tempThumbnailFile != null && tempThumbnailFile.exists()) {
                tempThumbnailFile.delete();
            }
            log.error("썸네일 생성 실패: fileName={}, error={}", inputFile.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "썸네일 생성 중 오류가 발생했습니다.");
        } finally {
            // 입력 임시 파일 정리
            if (tempInputFile != null && tempInputFile.exists()) {
                tempInputFile.delete();
            }
        }
    }

    @Override
    public int extractDuration(MultipartFile inputFile) throws IOException {
        File tempInputFile = null;
        
        try {
            // 1. 임시 파일 생성
            tempInputFile = createTempFile(inputFile, "duration_input");
            
            // 2. FFprobe 객체 생성
            FFprobe ffprobe = new FFprobe(ffprobeProperties.path());
            
            // 3. 동영상 정보 분석
            FFmpegProbeResult probeResult = ffprobe.probe(tempInputFile.getAbsolutePath());
            FFmpegStream videoStream = probeResult.getStreams().stream()
                    .filter(stream -> stream.codec_type == FFmpegStream.CodecType.VIDEO)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));
            
            // 4. duration 추출 (초 단위로 반올림)
            double durationSeconds = videoStream.duration > 0 ? videoStream.duration : 0.0;
            int duration = (int) Math.round(durationSeconds);
            
            log.info("동영상 길이 추출 완료: fileName={}, duration={}초", 
                    inputFile.getOriginalFilename(), duration);
            
            return duration;
            
        } catch (Exception e) {
            log.error("동영상 길이 추출 실패: fileName={}, error={}", inputFile.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 길이 추출 중 오류가 발생했습니다.");
        } finally {
            // 입력 임시 파일 정리
            if (tempInputFile != null && tempInputFile.exists()) {
                tempInputFile.delete();
            }
        }
    }
    
    private File createTempFile(MultipartFile inputFile, String prefix) throws IOException {
        String extension = getFileExtension(inputFile.getOriginalFilename());
        Path tempFile = Files.createTempFile(prefix + "_" + UUID.randomUUID(), "." + extension);
        Files.copy(inputFile.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile.toFile();
    }
    
    private File createTempOutputFile() throws IOException {
        Path tempFile = Files.createTempFile("compressed_" + UUID.randomUUID(), ".mp4");
        return tempFile.toFile();
    }

    private File createTempThumbnailFile() throws IOException {
        Path tempFile = Files.createTempFile("thumbnail_" + UUID.randomUUID(), ".jpg");
        return tempFile.toFile();
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "mp4";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
    
    private File compressUntilTargetSize(File inputFile, FFmpegProbeResult probeResult, FFmpeg ffmpeg, FFprobe ffprobe, String originalFileName) throws IOException {
        File currentOutputFile = null;
        int attempt = 0;
        final int maxAttempts = 5;
        
        // 초기 압축 설정 계산
        CompressionSettings settings = calculateCompressionSettings(inputFile.length(), probeResult);
        
        while (attempt < maxAttempts) {
            attempt++;
            currentOutputFile = createTempOutputFile();
            
            try {
                // FFmpeg 압축 실행
                FFmpegBuilder builder = new FFmpegBuilder()
                        .setInput(inputFile.getAbsolutePath())
                        .overrideOutputFiles(true)
                        .addOutput(currentOutputFile.getAbsolutePath())
                        .setFormat("mp4")
                        .setVideoCodec("libx264")
                        .setVideoResolution(settings.width(), settings.height())
                        .setVideoBitRate(settings.videoBitRate())
                        .setVideoFrameRate(settings.frameRate())
                        .setAudioCodec("aac")
                        .setAudioBitRate(settings.audioBitRate())
                        .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL)
                        .done();
                
                // 최대 길이 제한 적용
                if (probeResult.getStreams().stream()
                        .filter(stream -> stream.codec_type == FFmpegStream.CodecType.VIDEO)
                        .findFirst()
                        .map(stream -> stream.duration)
                        .filter(duration -> duration > MAX_DURATION_SECONDS)
                        .isPresent()) {
                    builder.addExtraArgs("-t", String.valueOf(MAX_DURATION_SECONDS));
                }
                
                FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
                executor.createJob(builder).run();
                
                log.info("압축 완료 (시도 {}): 원본={}MB, 압축={}MB", 
                        attempt, inputFile.length() / (1024 * 1024), currentOutputFile.length() / (1024 * 1024));
                
                // 목표 크기 확인
                if (currentOutputFile.length() <= TARGET_SIZE_BYTES) {
                    log.info("목표 크기 달성: {}MB", currentOutputFile.length() / (1024 * 1024));
                    return currentOutputFile;
                }
                
                // 목표 크기 초과 시 더 강한 압축 설정으로 재시도
                if (attempt < maxAttempts) {
                    settings = adjustCompressionSettings(settings, attempt);
                    log.info("목표 크기 초과, 재압축 시도 ({}차): 새로운 비트레이트={}kbps", 
                            attempt + 1, settings.videoBitRate() / 1000);
                    
                    // 이전 시도 파일 삭제
                    if (currentOutputFile.exists()) {
                        boolean deleted = currentOutputFile.delete();
                        if (!deleted) {
                            log.warn("이전 시도 파일 삭제 실패: {}", currentOutputFile.getAbsolutePath());
                        }
                    }
                }
                
            } catch (Exception e) {
                log.error("압축 시도 {} 실패: {}", attempt, e.getMessage());
                if (currentOutputFile.exists()) {
                    boolean deleted = currentOutputFile.delete();
                    if (!deleted) {
                        log.warn("실패한 압축 파일 삭제 실패: {}", currentOutputFile.getAbsolutePath());
                    }
                }
                
                if (attempt == maxAttempts) {
                    throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 압축에 실패했습니다.");
                }
                
                // 실패 시에도 설정 조정하여 재시도
                settings = adjustCompressionSettings(settings, attempt);
            }
        }
        
        // 최대 시도 후에도 목표 크기를 달성하지 못한 경우
        if (currentOutputFile.exists()) {
            log.warn("최대 시도 후에도 목표 크기 미달성: 최종 크기={}MB", currentOutputFile.length() / (1024 * 1024));
            return currentOutputFile; // 최선의 결과 반환
        }
        
        throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 압축에 실패했습니다.");
    }
    
    private CompressionSettings adjustCompressionSettings(CompressionSettings current, int attempt) {
        // 시도 횟수에 따라 점진적으로 압축률 증가
        double reductionFactor = Math.pow(0.7, attempt); // 70%씩 감소
        
        long newVideoBitRate = Math.max(500_000, (long) (current.videoBitRate() * reductionFactor)); // 최소 500kbps
        long newAudioBitRate = Math.max(64_000, (long) (current.audioBitRate() * reductionFactor)); // 최소 64kbps
        
        // 해상도도 필요시 조정
        int newWidth = current.width();
        int newHeight = current.height();
        
        if (attempt >= 3 && current.height() > 720) {
            // 3회 시도 후에는 720p로 강제 다운스케일
            newHeight = 720;
            newWidth = (int) (720 * ((double) current.width() / current.height()));
            newWidth = (newWidth / 8) * 8; // 8의 배수로 맞춤
        } else if (attempt >= 4 && current.height() > 480) {
            // 4회 시도 후에는 480p로 강제 다운스케일
            newHeight = 480;
            newWidth = (int) (480 * ((double) current.width() / current.height()));
            newWidth = (newWidth / 8) * 8; // 8의 배수로 맞춤
        }
        
        return new CompressionSettings(newWidth, newHeight, newVideoBitRate, newAudioBitRate, current.frameRate());
    }

    private CompressionSettings calculateCompressionSettings(long fileSizeBytes, FFmpegProbeResult probeResult) {
        FFmpegStream videoStream = probeResult.getStreams().stream()
                .filter(stream -> stream.codec_type == FFmpegStream.CodecType.VIDEO)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));
        
        // 원본 해상도
        int originalWidth = videoStream.width;
        int originalHeight = videoStream.height;
        double originalAspectRatio = (double) originalWidth / originalHeight;
        
        // 압축률 계산 (목표: 500MB 이하)
        double compressionRatio = (double) TARGET_SIZE_BYTES / fileSizeBytes;
        
        // 해상도 조정
        int targetWidth, targetHeight;
        if (compressionRatio < 0.3) {
            // 매우 큰 파일인 경우 720p로 다운스케일
            targetHeight = Math.min(720, originalHeight);
            targetWidth = (int) (targetHeight * originalAspectRatio);
        } else if (compressionRatio < 0.6) {
            // 큰 파일인 경우 1080p로 다운스케일
            targetHeight = Math.min(1080, originalHeight);
            targetWidth = (int) (targetHeight * originalAspectRatio);
        } else {
            // 그 외의 경우 원본 해상도 유지
            targetWidth = originalWidth;
            targetHeight = originalHeight;
        }
        
        // 8의 배수로 맞춤 (H.264 요구사항)
        targetWidth = (targetWidth / 8) * 8;
        targetHeight = (targetHeight / 8) * 8;
        
        // 비트레이트 계산
        long videoBitRate = calculateVideoBitRate(targetHeight, compressionRatio);
        long audioBitRate = 128_000; // 128kbps
        
        // 프레임레이트
        double frameRate = videoStream.r_frame_rate != null ? 
                videoStream.r_frame_rate.doubleValue() : 25.0;
        
        return new CompressionSettings(targetWidth, targetHeight, videoBitRate, audioBitRate, frameRate);
    }
    
    private long calculateVideoBitRate(int height, double compressionRatio) {
        // 해상도에 따른 기본 비트레이트
        long baseBitRate;
        if (height <= 480) {
            baseBitRate = 1_000_000; // 1Mbps
        } else if (height <= 720) {
            baseBitRate = 2_500_000; // 2.5Mbps
        } else if (height <= 1080) {
            baseBitRate = 5_000_000; // 5Mbps
        } else {
            baseBitRate = 8_000_000; // 8Mbps
        }
        
        // 압축률에 따라 조정
        return (long) (baseBitRate * Math.max(0.3, compressionRatio));
    }

    private record CompressionSettings(int width, int height, long videoBitRate, long audioBitRate, double frameRate) {
    }
}