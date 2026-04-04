package com.widyu.global.config;

import com.widyu.global.properties.FFmpegProperties;
import com.widyu.global.properties.FFprobeProperties;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FFmpegConfig {

    @Bean
    public FFmpeg ffmpeg(FFmpegProperties props) throws IOException {
        return new FFmpeg(props.path());
    }

    @Bean
    public FFprobe ffprobe(FFprobeProperties props) throws IOException {
        return new FFprobe(props.path());
    }
}
