package com.example.videostreaming.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class FileStorageService {

    @Value("${videos.dir}")
    private String storageDir;

    public File storeFile(MultipartFile file, String videoId) {
        // 영상별 디렉토리 생성
        File directory = new File(storageDir + File.separator + videoId);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        // 저장될 파일 경로 설정
        File destFile = new File(directory, file.getOriginalFilename());
        try {
            // 파일을 지정 경로로 이동
            file.transferTo(destFile);
        } catch (IOException e) {
            // 실제 서비스에서는 로깅 및 에러 핸들링 강화 필요
            throw new RuntimeException("파일 저장 중 에러 발생", e);
        }
        return destFile;
    }
}
