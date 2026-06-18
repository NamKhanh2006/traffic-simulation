package com.myteam.traffic.light;

import java.util.Optional;

/**
 * Đèn giao thông đếm 10 giây cuối.
 *
 * <p>Chỉ hiển thị đếm ngược khi số giây còn lại <= 10.
 * Các giây trước đó chỉ hiển thị màu đèn.</p>
 */
public class TenSecLight extends TrafficLight {
    public TenSecLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    @Override
    public Optional<Integer> getCountdownDisplay() {
        if (secondsRemaining <= 10) {
            return Optional.of(secondsRemaining);
        }
        return Optional.empty();
    }
}