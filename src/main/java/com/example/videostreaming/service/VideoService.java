package com.example.videostreaming.service;

import com.example.videostreaming.model.VideoMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final TranscodingService transcodingService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public String uploadVideo(MultipartFile file) {
        // 고유한 영상 ID 생성
        String videoId = UUID.randomUUID().toString();

        // 업로드된 파일을 로컬 파일 시스템에 저장
        File storedFile = fileStorageService.storeFile(file, videoId);

        try {
            // 파일로부터 영상 메타데이터(해상도 등) 추출
            VideoMetadata metadata = extractMetadata(storedFile);
            metadata.setVideoId(videoId);

            // 트랜스코딩 서비스를 호출하여 HLS 스트리밍 파일 생성
            transcodingService.transcodeVideo(storedFile, metadata);

            // 영상 ID 반환
            return videoId;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getMasterPlaylist(String videoId) {
        return transcodingService.getMasterPlaylist(videoId);
    }

    private VideoMetadata extractMetadata(File file) throws IOException {
        String command = String.format(
                "ffprobe -v quiet -print_format json -show_format -show_streams %s",
                file.getAbsolutePath());
        String output = runCommand(command);
        JsonNode probeResult = objectMapper.readTree(output);

        // 비디오 스트림 찾기
        JsonNode videoStream = findVideoStream(probeResult.get("streams"));
        if (videoStream == null) {
            throw new IllegalArgumentException("비디오 스트림이 없습니다.");
        }

        JsonNode format = probeResult.get("format");
        VideoMetadata metadata = new VideoMetadata();
        metadata.setWidth(videoStream.get("width").asInt());
        metadata.setHeight(videoStream.get("height").asInt());
        metadata.setCodec(videoStream.get("codec_name").asText());
        metadata.setDuration(format.get("duration").asDouble());
        metadata.setBitrate(getLongValue(format.get("bit_rate")));
        metadata.setFrameRate(parseFrameRate(videoStream.get("r_frame_rate").asText()));
        metadata.setRotation(getRotation(videoStream.get("tags")));

        return metadata;
    }

    /**
     * 주어진 명령어를 실행하고 출력을 반환한다.
     *
     * @param command 실행할 명령어 문자열
     * @return 명령어 실행 결과 출력 문자열
     * @throws IOException 실행 실패 또는 인터럽트 발생 시 예외 발생
     */
    private String runCommand(String command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command.split(" "));
        Process process = pb.start();

        String output = new BufferedReader(
                new InputStreamReader(process.getInputStream()))
                .lines().collect(Collectors.joining("\n"));

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("FFprobe 실행 실패: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFprobe 인터럽트 발생", e);
        }
        return output;
    }

    /**
     * FFprobe의 streams 배열에서 첫 번째 비디오 스트림을 찾는다.
     *
     * @param streams FFprobe가 반환한 streams JsonNode 배열
     * @return 비디오 스트림 JsonNode 또는 없으면 null
     */
    private JsonNode findVideoStream(JsonNode streams) {
        for (JsonNode stream : streams) {
            if ("video".equals(stream.get("codec_type").asText())) {
                return stream;
            }
        }
        return null;
    }

    /**
     * 주어진 프레임 레이트 문자열("num/den")을 파싱하여 FPS를 계산한다.
     *
     * @param frameRate 프레임 레이트 문자열
     * @return 계산된 FPS 값 또는 파싱 실패 시 0.0
     */
    private double parseFrameRate(String frameRate) {
        try {
            String[] parts = frameRate.split("/");
            if (parts.length == 2) {
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            }
        } catch (Exception e) {
            // 파싱 실패 시 0.0 반환
        }
        return 0.0;
    }

    /**
     * 주어진 JsonNode에서 long 값을 추출한다.
     *
     * @param node 추출할 JsonNode
     * @return 값이 있으면 long 값, 없으면 0
     */
    private long getLongValue(JsonNode node) {
        if (node != null && !node.isNull()) {
            try {
                return node.asLong();
            } catch (NumberFormatException e) {
                // 파싱 실패 시 0 반환
            }
        }
        return 0L;
    }

    /**
     * 태그(JsonNode) 내의 rotate 값을 정수로 추출한다.
     *
     * @param tags 비디오 스트림의 태그 JsonNode
     * @return 회전 값, 없거나 파싱 실패 시 0
     */
    private int getRotation(JsonNode tags) {
        if (tags != null && tags.has("rotate")) {
            try {
                return tags.get("rotate").asInt();
            } catch (NumberFormatException e) {
                // 파싱 실패 시 0 반환
            }
        }
        return 0;
    }
}
