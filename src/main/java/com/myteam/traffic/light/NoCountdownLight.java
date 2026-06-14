package com.myteam.traffic.light;

import java.util.Optional;

/**
 * Đèn không hiển thị đếm ngược — chỉ hiện màu đèn, không có số giây.
 *
 * <p><b>Lưu ý quan trọng:</b> đèn vẫn đếm giờ nội bộ bình thường,
 * chỉ là không hiển thị số ra ngoài.
 * Logic thời gian giống hệt {@link CountdownLight} —
 * sự khác biệt chỉ nằm ở {@link #getCountdownDisplay()}.</p>
 */
public class NoCountdownLight extends TrafficLight {


    public NoCountdownLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    /**
     * Không hiển thị số giây — renderer bỏ qua, chỉ vẽ màu đèn.
     */
    @Override
    public Optional<Integer> getCountdownDisplay() {
        return Optional.empty();
    }
}