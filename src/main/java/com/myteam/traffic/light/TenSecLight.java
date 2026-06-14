package com.myteam.traffic.light;

import java.util.Optional;

/**
 * Đèn chỉ hiển thị đếm ngược khi thời gian còn lại ≤ 10 giây.
 */
public class TenSecLight extends TrafficLight {

    public TenSecLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    /**
     * Chỉ trả về số giây nếu còn từ 10 trở xuống.
     */
    @Override
    public Optional<Integer> getCountdownDisplay() {
        if (secondsRemaining <= 10) {
            return Optional.of(secondsRemaining);
        }
        return Optional.empty();
    }
}