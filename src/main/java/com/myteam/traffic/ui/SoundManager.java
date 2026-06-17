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

    /**
     * Dừng phát âm thanh theo tên file.
     * Dùng khi xe ưu tiên bị xóa khỏi mô phỏng để tắt tiếng còi.
     */
    public static void stopSound(String fileName) {
        try {
            AudioClip clip = soundCache.get(fileName);
            if (clip != null) {
                clip.stop();
            }
        } catch (Exception e) {
            // Bỏ qua lỗi khi dừng
        }
    }

    /**
     * Dừng tất cả âm thanh đang phát.
     */
    public static void stopAll() {
        for (AudioClip clip : soundCache.values()) {
            if (clip != null) {
                try { clip.stop(); } catch (Exception e) {}
            }
        }
    }
}