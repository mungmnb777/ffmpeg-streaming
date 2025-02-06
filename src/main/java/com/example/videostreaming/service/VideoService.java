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
        List<String> command = new ArrayList<>();
        command.add("ffprobe");
        command.add("-v");
        command.add("quiet");
        command.add("-print_format");
        command.add("json");
        command.add("-show_format");
        command.add("-show_streams");
        command.add(file.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        // ffprobe 출력 결과 읽기
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // 프로세스 종료 대기
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("FFprobe 실행 실패: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFprobe 인터럽트 발생", e);
        }

        JsonNode probeResult = objectMapper.readTree(output.toString());

        // 비디오 스트림 찾기
        JsonNode videoStream = null;
        JsonNode streams = probeResult.get("streams");
        for (JsonNode stream : streams) {
            if ("video".equals(stream.get("codec_type").asText())) {
                videoStream = stream;
                break;
            }
        }

        if (videoStream == null) {
            throw new IllegalArgumentException("비디오 스트림이 없습니다.");
        }

        JsonNode format = probeResult.get("format");

        VideoMetadata metadata = new VideoMetadata();

        // 기본 메타데이터 설정
        metadata.setWidth(videoStream.get("width").asInt());
        metadata.setHeight(videoStream.get("height").asInt());

        // 추가 메타데이터 설정
        metadata.setCodec(videoStream.get("codec_name").asText());
        metadata.setDuration(format.get("duration").asDouble());

        // bit_rate가 문자열로 되어있을 수 있으므로 처리
        JsonNode bitRateNode = format.get("bit_rate");
        if (bitRateNode != null && !bitRateNode.isNull()) {
            try {
                metadata.setBitrate(bitRateNode.asLong());
            } catch (NumberFormatException e) {
                metadata.setBitrate(0);
            }
        }

        // 프레임레이트 계산 (r_frame_rate는 "24000/1001"과 같은 형식)
        String frameRate = videoStream.get("r_frame_rate").asText();
        try {
            String[] parts = frameRate.split("/");
            if (parts.length == 2) {
                double fps = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
                metadata.setFrameRate(fps);
            }
        } catch (NumberFormatException | ArithmeticException e) {
            metadata.setFrameRate(0.0);
        }

        // 회전 정보 확인
        JsonNode tags = videoStream.get("tags");
        if (tags != null && tags.has("rotate")) {
            try {
                metadata.setRotation(tags.get("rotate").asInt());
            } catch (NumberFormatException e) {
                metadata.setRotation(0);
            }
        }

        return metadata;
    }
}
