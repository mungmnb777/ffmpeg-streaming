package com.example.videostreaming.controller;

import com.example.videostreaming.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Value("${videos.dir}")
    private String videosDir;

    private final VideoService videoService;

    @Autowired
    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadVideo(@RequestParam("file") MultipartFile file) {
        // VideoService에 업로드를 위임
        String videoId = videoService.uploadVideo(file);
        return ResponseEntity.ok(videoId);
    }

    @GetMapping("/{videoId}/stream/master.m3u8")
    public ResponseEntity<String> getMasterPlaylist(@PathVariable String videoId) {
        // VideoService에 마스터 플레이리스트 조회를 위임
        String masterPlaylist = videoService.getMasterPlaylist(videoId);
        return ResponseEntity.ok(masterPlaylist);
    }

    @GetMapping("/{videoId}/stream/{resolution}/{fileName:.+}")
    public ResponseEntity<Resource> getVariantResource(@PathVariable String videoId,
                                                       @PathVariable String resolution,
                                                       @PathVariable String fileName) {
        File file = new File(videosDir + File.separator + videoId + File.separator + resolution + File.separator + fileName);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (fileName.endsWith(".m3u8")) {
            mediaType = MediaType.parseMediaType("application/vnd.apple.mpegurl");
        } else if (fileName.endsWith(".ts")) {
            mediaType = MediaType.parseMediaType("video/mp2t");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }
}
