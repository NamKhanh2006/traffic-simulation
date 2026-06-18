/*
package com.myteam.traffic.light;

public class CountdownLight extends TrafficLight {
    public CountdownLight(int redTime, int greenTime, int yellowTime) {
        super(redTime, greenTime, yellowTime);
    }

    @Override
    public void changeState() {
        if (secondsRemaining > 1) {
            secondsRemaining--;
            return;
        }

        TrafficLightState next = nextState(currentState);
        switchTo(next);
    }
}
*/

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

    // Logic tick đã được xử lý ở lớp cha TrafficLight

    /**
     * Luôn hiển thị số giây còn lại.
     */

	@Override
    public Optional<Integer> getCountdownDisplay() {
        return Optional.of(secondsRemaining);
    }
}
