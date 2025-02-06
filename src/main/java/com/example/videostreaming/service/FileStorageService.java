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
        File directory = new File(storageDir + File.separator + videoId);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File destFile = new File(directory, file.getOriginalFilename());
        try {
            file.transferTo(destFile);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 에러 발생", e);
        }
        return destFile;
    }
}
