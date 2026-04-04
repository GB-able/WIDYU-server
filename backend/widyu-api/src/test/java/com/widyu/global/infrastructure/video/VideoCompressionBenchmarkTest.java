package com.widyu.global.infrastructure.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 로컬 전용 벤치마크 테스트 — CI에서는 자동 스킵됩니다.
 * 실행 조건: FFmpeg 로컬 설치 + test_300mb.mp4 파일 존재
 */
class VideoCompressionBenchmarkTest {

    private static final String FFMPEG_PATH  = "/opt/homebrew/bin/ffmpeg";
    private static final String FFPROBE_PATH = "/opt/homebrew/bin/ffprobe";
    private static final String TEST_VIDEO   = "video/test_300mb.mp4";
    private static final int    RUNS         = 5;

    private FFmpegVideoCompressionService service;
    private File testVideoAsFile;
    private long fileSizeMB;

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        assumeTrue(new File(FFMPEG_PATH).exists(),  "FFmpeg 없음 — 로컬 전용 테스트, CI 스킵");
        assumeTrue(new File(FFPROBE_PATH).exists(), "FFprobe 없음 — 로컬 전용 테스트, CI 스킵");

        URL url = getClass().getClassLoader().getResource(TEST_VIDEO);
        assumeTrue(url != null, "test_300mb.mp4 없음 — 로컬 전용 테스트, CI 스킵");

        FFmpeg  ffmpeg  = new FFmpeg(FFMPEG_PATH);
        FFprobe ffprobe = new FFprobe(FFPROBE_PATH);
        service = new FFmpegVideoCompressionService(ffmpeg, ffprobe);

        testVideoAsFile = new File(url.toURI());
        fileSizeMB = testVideoAsFile.length() / (1024 * 1024);
        System.out.printf("[setUp] 파일 크기: %d MB%n", fileSizeMB);
    }

    @Test
    @DisplayName("[After] generateThumbnail(File) — 파일 복사 없음, 5회 평균")
    void measure_generateThumbnail_after() throws IOException {
        int duration = service.extractDuration(testVideoAsFile);
        List<Long> times = new ArrayList<>();

        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            File result = service.generateThumbnail(testVideoAsFile, (double) duration);
            times.add(System.nanoTime() - start);
            if (result != null && result.exists()) result.delete();
            System.out.printf("  run %d: %d ms%n", i + 1, times.get(i) / 1_000_000);
        }
        long avgMs = times.stream().mapToLong(t -> t).sum() / RUNS / 1_000_000;
        printResult("generateThumbnail (After) ", times);
        System.out.printf("  Before 평균: 660 ms → 개선: %+d ms%n", avgMs - 660);
    }

    @Test
    @DisplayName("[After] extractDuration(File) — 파일 복사 없음, 5회 평균")
    void measure_extractDuration_after() throws IOException {
        List<Long> times = new ArrayList<>();
        int duration = 0;

        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            duration = service.extractDuration(testVideoAsFile);
            times.add(System.nanoTime() - start);
            System.out.printf("  run %d: %d ms%n", i + 1, times.get(i) / 1_000_000);
        }
        long avgMs = times.stream().mapToLong(t -> t).sum() / RUNS / 1_000_000;
        printResult("extractDuration   (After) ", times);
        System.out.printf("  Before 평균: 396 ms → 개선: %+d ms%n", avgMs - 396);
        System.out.printf("  추출된 영상 길이: %d초%n", duration);
    }

    @Test
    @DisplayName("[After] 통합 파이프라인 (extractDuration + generateThumbnail), 5회 평균")
    void measure_pipeline_after() throws IOException {
        List<Long> times = new ArrayList<>();

        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            int duration = service.extractDuration(testVideoAsFile);
            File thumb = service.generateThumbnail(testVideoAsFile, (double) duration);
            times.add(System.nanoTime() - start);
            if (thumb != null && thumb.exists()) thumb.delete();
            System.out.printf("  run %d: %d ms%n", i + 1, times.get(i) / 1_000_000);
        }
        long avgMs = times.stream().mapToLong(t -> t).sum() / RUNS / 1_000_000;
        printResult("통합 파이프라인   (After) ", times);
        System.out.printf("  Before 평균: 902 ms → 개선: %+d ms (%.0f%%)%n",
                avgMs - 902, (902 - avgMs) * 100.0 / 902);
        System.out.printf("  불필요 복사량: 0 MB (Before: %d MB × 2회 = %d MB 제거)%n",
                fileSizeMB, fileSizeMB * 2);
    }

    private void printResult(String label, List<Long> nanoTimes) {
        long avgMs = nanoTimes.stream().mapToLong(t -> t).sum() / nanoTimes.size() / 1_000_000;
        long minMs = nanoTimes.stream().mapToLong(t -> t).min().orElse(0) / 1_000_000;
        long maxMs = nanoTimes.stream().mapToLong(t -> t).max().orElse(0) / 1_000_000;
        System.out.printf("=== %s | %d MB | %d회 ===%n", label, fileSizeMB, nanoTimes.size());
        System.out.printf("  평균: %d ms | 최소: %d ms | 최대: %d ms%n", avgMs, minMs, maxMs);
    }
}
