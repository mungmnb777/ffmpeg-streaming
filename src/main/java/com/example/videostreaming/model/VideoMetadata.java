package com.example.videostreaming.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VideoMetadata {
    private String videoId;
    private int width;
    private int height;
    private String codec;
    private double duration;
    private long bitrate;
    private double frameRate;
    private int rotation;
}