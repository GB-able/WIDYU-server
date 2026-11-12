package com.widyu.global.infrastructure.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 FFmpeg로 길이 자르기를 확인한다. FFmpeg가 없는 환경(CI)에서는 건너뛴다.
 */
class FFmpegVideoTrimTest {

    private static final String FFMPEG_PATH = "/opt/homebrew/bin/ffmpeg";
    private static final String FFPROBE_PATH = "/opt/homebrew/bin/ffprobe";

    @Test
    @DisplayName("70초 영상을 자르면 60초 영상이 된다")
    void 일분을_넘는_영상은_60초로_잘린다() throws Exception {
        // given
        FFmpegVideoCompressionService service = service();
        File video = generateVideo(70);

        // when
        Optional<File> trimmed = service.trimToMaxDuration(video);

        // then
        assertThat(trimmed).isPresent();
        assertThat(service.extractDuration(trimmed.get())).isEqualTo(60);
        Files.deleteIfExists(video.toPath());
        Files.deleteIfExists(trimmed.get().toPath());
    }

    @Test
    @DisplayName("60초를 1초 미만으로 넘는 영상도 자른다")
    void 반올림하면_60초인_영상도_자른다() throws Exception {
        // given
        FFmpegVideoCompressionService service = service();
        File video = generateVideo(60.4);

        // when
        Optional<File> trimmed = service.trimToMaxDuration(video);

        // then
        assertThat(trimmed).isPresent();
        Files.deleteIfExists(video.toPath());
        Files.deleteIfExists(trimmed.get().toPath());
    }

    @Test
    @DisplayName("스트림 길이가 없는 MKV 영상도 컨테이너 길이로 판단해 60초로 자른다")
    void 스트림_길이가_없는_MKV도_60초로_잘린다() throws Exception {
        // given
        FFmpegVideoCompressionService service = service();
        File video = generateVideo(70, ".mkv");

        // when
        Optional<File> trimmed = service.trimToMaxDuration(video);

        // then
        assertThat(trimmed).isPresent();
        assertThat(service.extractDuration(trimmed.get())).isEqualTo(60);
        Files.deleteIfExists(video.toPath());
        Files.deleteIfExists(trimmed.get().toPath());
    }

    @Test
    @DisplayName("60초 이하 영상은 자르지 않는다")
    void 일분_이하_영상은_자르지_않는다() throws Exception {
        // given
        FFmpegVideoCompressionService service = service();
        File video = generateVideo(60);

        // when
        Optional<File> trimmed = service.trimToMaxDuration(video);

        // then
        assertThat(trimmed).isEmpty();
        Files.deleteIfExists(video.toPath());
    }

    private FFmpegVideoCompressionService service() throws IOException {
        assumeTrue(new File(FFMPEG_PATH).exists(), "FFmpeg 없음 — 로컬 전용 테스트, CI 스킵");
        assumeTrue(new File(FFPROBE_PATH).exists(), "FFprobe 없음 — 로컬 전용 테스트, CI 스킵");
        return new FFmpegVideoCompressionService(new FFmpeg(FFMPEG_PATH), new FFprobe(FFPROBE_PATH));
    }

    private File generateVideo(double seconds) throws Exception {
        return generateVideo(seconds, ".mp4");
    }

    private File generateVideo(double seconds, String extension) throws Exception {
        File output = Files.createTempFile("trim_source_", extension).toFile();
        Process process = new ProcessBuilder(FFMPEG_PATH, "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=size=160x120:rate=5",
                "-t", String.valueOf(seconds), "-c:v", "libx264", "-preset", "ultrafast", "-g", "5",
                output.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
        assertThat(process.waitFor()).isZero();
        return output;
    }
}
