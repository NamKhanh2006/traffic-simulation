package com.myteam.traffic.ui;

import javafx.scene.media.AudioClip;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    
    // Bộ đệm lưu trữ các file âm thanh đã được tải để tránh việc tải lại nhiều lần
    private static final Map<String, AudioClip> soundCache = new HashMap<>();

    public static void playSound(String fileName) {
        try {
            AudioClip clip = soundCache.computeIfAbsent(fileName, key -> {
                var resource = SoundManager.class.getResource("/sounds/" + key);
                if (resource != null) {
                    return new AudioClip(resource.toExternalForm());
                } else {
                    System.out.println("Không tìm thấy file âm thanh: /sounds/" + key);
                    return null;
                }
            });
            
            if (clip != null) {
                clip.play(); // Phát âm thanh
            }
        } catch (Exception e) {
            System.out.println("Lỗi phát âm thanh " + fileName + ": " + e.getMessage());
        }
    }
}