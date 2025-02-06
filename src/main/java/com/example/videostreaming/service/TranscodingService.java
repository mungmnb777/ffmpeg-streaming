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
        List<String> qualities = determineQualities(metadata);

        for (String quality : qualities) {
            transcodeForQuality(inputFile, metadata.getVideoId(), quality);
        }

        generateMasterPlaylist(metadata.getVideoId(), qualities);
    }

    private List<String> determineQualities(VideoMetadata metadata) {
        List<String> qualities = new ArrayList<>();

        qualities.add("480p");

        if (metadata.getHeight() >= 720) {
            qualities.add("720p");
        }

        if (metadata.getHeight() >= 1080) {
            qualities.add("1080p");
        }
        return qualities;
    }

    private void transcodeForQuality(File inputFile, String videoId, String quality) {
        String resolution = getResolutionForQuality(quality);

        File outputDir = new File(videosDir + File.separator + videoId + File.separator + quality);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

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
            throw new RuntimeException("트랜스코딩 중 에러 발생", e);
        }
    }

    private void generateMasterPlaylist(String videoId, List<String> qualities) {
        StringBuilder masterPlaylist = new StringBuilder("#EXTM3U\n");
        for (String quality : qualities) {
            masterPlaylist.append(String.format("#EXT-X-STREAM-INF:BANDWIDTH=%s,RESOLUTION=%s\n",
                    getBandwidth(quality), getResolutionForQuality(quality)));
            masterPlaylist.append(String.format("%s/master.m3u8\n", quality));
        }

        File masterFile = new File(videosDir + File.separator + videoId + File.separator + "master.m3u8");
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
