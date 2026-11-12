package com.widyu.global.infrastructure.video;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
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

    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;

    private static final long TARGET_SIZE_BYTES = 500 * 1024 * 1024; // 500MB
    private static final int MAX_DURATION_SECONDS = 60; // 1분 (#1010)

    @Override
    public File compressVideo(MultipartFile inputFile) throws IOException {
        File tempInputFile = null;

        try {
            tempInputFile = createTempFile(inputFile, "input");
            FFmpegProbeResult probeResult = ffprobe.probe(tempInputFile.getAbsolutePath());

            probeResult.getStreams().stream()
                    .filter(s -> s.codec_type == FFmpegStream.CodecType.VIDEO)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));

            File finalOutputFile = compressUntilTargetSize(tempInputFile, probeResult, inputFile.getOriginalFilename());

            log.info("동영상 압축 완료: 원본={}MB, 최종={}MB",
                    inputFile.getSize() / (1024 * 1024),
                    finalOutputFile.length() / (1024 * 1024));

            return finalOutputFile;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("동영상 압축 실패: errorType={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 압축 중 오류가 발생했습니다.");
        } finally {
            deleteSilently(tempInputFile);
        }
    }

    @Override
    public boolean needsCompression(MultipartFile file) {
        return file.getSize() > TARGET_SIZE_BYTES;
    }

    @Override
    public void cleanupTempFile(File file) {
        deleteSilently(file);
    }

    /**
     * 압축이 완료된 File을 직접 받아 썸네일을 추출합니다.
     * 추출 지점은 영상 길이의 10% 지점(최소 1초)으로 동적 계산합니다.
     */
    @Override
    public File generateThumbnail(File inputFile, double durationSeconds) throws IOException {
        File tempThumbnailFile = null;

        try {
            tempThumbnailFile = createTempOutputFile("thumbnail", ".jpg");

            double seekSeconds = Math.max(1.0, durationSeconds * 0.1);

            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(inputFile.getAbsolutePath())
                    .overrideOutputFiles(true)
                    .addOutput(tempThumbnailFile.getAbsolutePath())
                    .setFormat("image2")
                    .setVideoCodec("mjpeg")
                    .setVideoFrameRate(1)
                    .setVideoResolution(640, 480)
                    .addExtraArgs("-ss", String.valueOf(seekSeconds))
                    .addExtraArgs("-vframes", "1")
                    .addExtraArgs("-q:v", "2")
                    .done();

            new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();

            log.info("썸네일 생성 완료: seekSec={}, size={}KB",
                    seekSeconds, tempThumbnailFile.length() / 1024);

            return tempThumbnailFile;

        } catch (Exception e) {
            deleteSilently(tempThumbnailFile);
            log.error("썸네일 생성 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "썸네일 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * 압축이 완료된 File을 직접 받아 영상 길이를 추출합니다.
     * 임시 파일 복사 없이 FFprobe로 직접 분석합니다.
     */
    @Override
    public int extractDuration(File inputFile) throws IOException {
        try {
            int duration = (int) Math.round(probeDurationSeconds(inputFile));
            log.info("동영상 길이 추출 완료: duration={}초", duration);
            return duration;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("동영상 길이 추출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 길이 추출 중 오류가 발생했습니다.");
        }
    }

    /**
     * 최대 길이를 넘는 영상의 앞부분만 남긴다. 재인코딩 없이 스트림을 복사하므로 화질이 그대로이고 빠르다.
     * 압축 경로는 압축할 때 같은 상한을 적용하므로, 이 메서드는 압축하지 않는 영상의 상한을 맡는다.
     */
    @Override
    public Optional<File> trimToMaxDuration(File inputFile) throws IOException {
        File trimmedFile = null;
        try {
            // 반올림한 정수 길이로 비교하면 60.4초 같은 영상이 빠지므로 실제 길이로 판단한다.
            // 길이를 알 수 없으면(0) 자른다. -t 60은 60초 이하 영상을 그대로 둔다.
            double durationSeconds = probeDurationSeconds(inputFile);
            if (durationSeconds > 0 && durationSeconds <= MAX_DURATION_SECONDS) {
                return Optional.empty();
            }

            trimmedFile = createTempOutputFile("trimmed", "." + getFileExtension(inputFile.getName()));
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(inputFile.getAbsolutePath())
                    .overrideOutputFiles(true)
                    .addOutput(trimmedFile.getAbsolutePath())
                    // 출력 빌더의 인자라 `-i <입력> -c copy -t 60 <출력>` 순서가 된다 (FFmpegVideoTrimTest로 실제 실행 확인)
                    .addExtraArgs("-c", "copy")
                    .addExtraArgs("-t", String.valueOf(MAX_DURATION_SECONDS))
                    .done();

            new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();

            log.info("동영상 길이 자르기 완료: 원본={}초, 상한={}초", durationSeconds, MAX_DURATION_SECONDS);
            return Optional.of(trimmedFile);

        } catch (Exception e) {
            deleteSilently(trimmedFile);
            log.error("동영상 길이 자르기 실패: errorType={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 길이를 줄이지 못했습니다.");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 압축 로직
    // ──────────────────────────────────────────────────────────────────────

    private File compressUntilTargetSize(File inputFile, FFmpegProbeResult probeResult, String originalFileName) throws IOException {
        CompressionSettings settings = calculateCompressionSettings(inputFile.length(), probeResult);
        File currentOutputFile = null;
        final int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            currentOutputFile = createTempOutputFile("compressed", ".mp4");

            try {
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

                if (probeResult.getStreams().stream()
                        .filter(s -> s.codec_type == FFmpegStream.CodecType.VIDEO)
                        .findFirst()
                        .map(s -> s.duration)
                        .filter(d -> d > MAX_DURATION_SECONDS)
                        .isPresent()) {
                    builder.addExtraArgs("-t", String.valueOf(MAX_DURATION_SECONDS));
                }

                new FFmpegExecutor(ffmpeg, ffprobe).createJob(builder).run();

                log.info("압축 완료 (시도 {}): 원본={}MB, 압축={}MB",
                        attempt, inputFile.length() / (1024 * 1024), currentOutputFile.length() / (1024 * 1024));

                if (currentOutputFile.length() <= TARGET_SIZE_BYTES) {
                    log.info("목표 크기 달성: {}MB", currentOutputFile.length() / (1024 * 1024));
                    return currentOutputFile;
                }

                if (attempt < maxAttempts) {
                    settings = adjustCompressionSettings(settings, attempt);
                    log.info("목표 크기 초과, 재압축 ({}차): 새 비트레이트={}kbps",
                            attempt + 1, settings.videoBitRate() / 1000);
                    deleteSilently(currentOutputFile);
                }

            } catch (Exception e) {
                log.error("압축 시도 {} 실패: {}", attempt, e.getMessage());
                deleteSilently(currentOutputFile);
                if (attempt == maxAttempts) {
                    throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 압축에 실패했습니다.");
                }
                settings = adjustCompressionSettings(settings, attempt);
            }
        }

        if (currentOutputFile != null && currentOutputFile.exists()) {
            log.warn("최대 시도 후 목표 크기 미달성: 최종={}MB", currentOutputFile.length() / (1024 * 1024));
            return currentOutputFile;
        }

        throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "동영상 압축에 실패했습니다.");
    }

    private CompressionSettings adjustCompressionSettings(CompressionSettings current, int attempt) {
        double reductionFactor = Math.pow(0.7, attempt);
        long newVideoBitRate = Math.max(500_000, (long) (current.videoBitRate() * reductionFactor));
        long newAudioBitRate = Math.max(64_000,  (long) (current.audioBitRate() * reductionFactor));

        int newWidth  = current.width();
        int newHeight = current.height();

        if (attempt >= 3 && current.height() > 720) {
            newHeight = 720;
            newWidth  = alignTo8((int) (720 * ((double) current.width() / current.height())));
        } else if (attempt >= 4 && current.height() > 480) {
            newHeight = 480;
            newWidth  = alignTo8((int) (480 * ((double) current.width() / current.height())));
        }

        return new CompressionSettings(newWidth, newHeight, newVideoBitRate, newAudioBitRate, current.frameRate());
    }

    private CompressionSettings calculateCompressionSettings(long fileSizeBytes, FFmpegProbeResult probeResult) {
        FFmpegStream videoStream = probeResult.getStreams().stream()
                .filter(s -> s.codec_type == FFmpegStream.CodecType.VIDEO)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));

        int originalWidth  = videoStream.width;
        int originalHeight = videoStream.height;
        double aspectRatio = (double) originalWidth / originalHeight;
        double compressionRatio = (double) TARGET_SIZE_BYTES / fileSizeBytes;

        int targetHeight, targetWidth;
        if (compressionRatio < 0.3) {
            targetHeight = Math.min(720,  originalHeight);
        } else if (compressionRatio < 0.6) {
            targetHeight = Math.min(1080, originalHeight);
        } else {
            targetHeight = originalHeight;
        }
        targetWidth = alignTo8((int) (targetHeight * aspectRatio));
        targetHeight = alignTo8(targetHeight);

        long videoBitRate = calculateVideoBitRate(targetHeight, compressionRatio);
        double frameRate = 25.0;
        if (videoStream.r_frame_rate != null) {
            frameRate = videoStream.r_frame_rate.doubleValue();
        }

        return new CompressionSettings(targetWidth, targetHeight, videoBitRate, 128_000L, frameRate);
    }

    private long calculateVideoBitRate(int height, double compressionRatio) {
        long baseBitRate;
        if      (height <= 480)  baseBitRate = 1_000_000L;
        else if (height <= 720)  baseBitRate = 2_500_000L;
        else if (height <= 1080) baseBitRate = 5_000_000L;
        else                     baseBitRate = 8_000_000L;
        return (long) (baseBitRate * Math.max(0.3, compressionRatio));
    }

    // ──────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────

    private File createTempFile(MultipartFile inputFile, String prefix) throws IOException {
        String extension = getFileExtension(inputFile.getOriginalFilename());
        Path tempFile = Files.createTempFile(prefix + "_" + UUID.randomUUID(), "." + extension);
        Files.copy(inputFile.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile.toFile();
    }

    private File createTempOutputFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix + "_" + UUID.randomUUID(), suffix).toFile();
    }

    private void deleteSilently(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                log.warn("임시 파일 삭제 실패: {}", file.getAbsolutePath());
            }
        }
    }

    private int alignTo8(int value) {
        return (value / 8) * 8;
    }

    private double probeDurationSeconds(File inputFile) throws IOException {
        FFmpegProbeResult probeResult = ffprobe.probe(inputFile.getAbsolutePath());
        double streamDuration = probeResult.getStreams().stream()
                .filter(s -> s.codec_type == FFmpegStream.CodecType.VIDEO)
                .findFirst()
                .map(this::nonNegativeDuration)
                .orElse(0.0);
        if (streamDuration > 0) {
            return streamDuration;
        }
        // MKV, WebM은 비디오 스트림에 길이를 기록하지 않기도 해서 컨테이너 길이로 읽는다
        if (probeResult.getFormat() != null && probeResult.getFormat().duration > 0) {
            return probeResult.getFormat().duration;
        }
        return 0.0;
    }

    private double nonNegativeDuration(FFmpegStream stream) {
        if (stream.duration > 0) {
            return stream.duration;
        }
        return 0.0;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "mp4";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private record CompressionSettings(int width, int height, long videoBitRate, long audioBitRate, double frameRate) {}
}
