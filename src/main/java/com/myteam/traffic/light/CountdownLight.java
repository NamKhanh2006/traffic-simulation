package com.myteam.traffic.light;

import java.util.Optional;

/**
 * Đèn luôn hiển thị đếm ngược (loại phổ biến nhất).
 *
 * <p>Ví dụ: đèn đỏ 30 giây → mặt đèn hiển thị 30, 29, 28, ... 1, rồi chuyển xanh.</p>
 */

public class CountdownLight extends TrafficLight {

    public CountdownLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    /**
     * Luôn hiển thị số giây còn lại.
     */

	@Override
    public Optional<Integer> getCountdownDisplay() {
        return Optional.of(secondsRemaining);
    }
}
