package com.example.videostreaming.service;

import com.example.videostreaming.model.VideoMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class TranscodingService {

    @Value("${videos.dir}")
    private String videosDir;

    public void transcodeVideo(File inputFile, VideoMetadata metadata) {
        // 영상의 원본 해상도에 따라 제공할 품질 리스트 결정
        List<String> qualities = determineQualities(metadata);

        // 결정된 각 품질에 대해 트랜스코딩 실행
        for (String quality : qualities) {
            transcodeForQuality(inputFile, metadata.getVideoId(), quality);
        }

        // 적응형 스트리밍을 위한 마스터 플레이리스트 생성
        generateMasterPlaylist(metadata.getVideoId(), qualities);
    }

    private List<String> determineQualities(VideoMetadata metadata) {
        List<String> qualities = new ArrayList<>();
        // 480p는 항상 포함 (예외적으로 아무리 낮은 해상도라도 제공)
        qualities.add("480p");

        // 원본 해상도가 720p 이상이면 720p 제공
        if (metadata.getHeight() >= 720) {
            qualities.add("720p");
        }
        // 원본 해상도가 1080p 이상이면 1080p 제공
        if (metadata.getHeight() >= 1080) {
            qualities.add("1080p");
        }
        return qualities;
    }

    private void transcodeForQuality(File inputFile, String videoId, String quality) {
        // 품질별 목표 해상도 결정
        String resolution = getResolutionForQuality(quality);

        // 출력 디렉토리 생성 (예: videos/{videoId}/480p)
        File outputDir = new File(videosDir + File.separator + videoId + File.separator + quality);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // FFmpeg 명령어 작성 (간략화된 예제)
        String command = String.format("ffmpeg -i %s -vf scale=%s -hls_time 10 -hls_playlist_type vod %s/master.m3u8",
                inputFile.getAbsolutePath(), resolution, outputDir.getAbsolutePath());

        // 실제 명령어 실행
        executeCommand(command);
    }

    private String getResolutionForQuality(String quality) {
        return switch (quality) {
            case "720p" -> "1280:720";
            case "1080p" -> "1920:1080";
            default -> "640:480";
        };
    }

    private void executeCommand(String command) {
        try {
            // ProcessBuilder를 사용하여 명령어 실행
            ProcessBuilder builder = new ProcessBuilder(command.split(" "));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            // 명령어 실행 결과 출력(디버깅용)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            // 실제 서비스에서는 적절한 로깅 및 에러 핸들링이 필요함
            throw new RuntimeException("트랜스코딩 중 에러 발생", e);
        }
    }

    private void generateMasterPlaylist(String videoId, List<String> qualities) {
        StringBuilder masterPlaylist = new StringBuilder("#EXTM3U\n");
        for (String quality : qualities) {
            // BANDWIDTH 및 RESOLUTION 정보는 예제이므로 실제 환경에 맞게 조정 필요
            masterPlaylist.append(String.format("#EXT-X-STREAM-INF:BANDWIDTH=%s,RESOLUTION=%s\n",
                    getBandwidth(quality), getResolutionForQuality(quality)));
            masterPlaylist.append(String.format("%s/master.m3u8\n", quality));
        }

        // 마스터 플레이리스트 파일을 영상 디렉토리에 작성
        File masterFile = new File(videosDir + videoId + "/master.m3u8");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(masterFile))) {
            writer.write(masterPlaylist.toString());
        } catch (IOException e) {
            throw new RuntimeException("마스터 플레이리스트 작성 중 에러 발생", e);
        }
    }

    private String getBandwidth(String quality) {
        return switch (quality) {
            case "720p" -> "1400000";
            case "1080p" -> "2800000";
            default -> "800000";
        };
    }

    public String getMasterPlaylist(String videoId) {
        File masterFile = new File(videosDir + File.separator + videoId + "/master.m3u8");
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(masterFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("마스터 플레이리스트 읽기 중 에러 발생", e);
        }
        return content.toString();
    }
}
