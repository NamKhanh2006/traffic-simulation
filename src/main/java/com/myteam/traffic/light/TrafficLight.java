package com.myteam.traffic.light;

import java.util.Optional;

/**
 * Đèn giao thông trừu tượng.
 *
 * <p>Vòng trạng thái cố định: RED → GREEN → YELLOW → RED → ...</p>
 *
 * <p>Mỗi subclass quyết định <b>khi nào</b> chuyển trạng thái
 * bằng cách override {@link #tick()}.
 * Renderer quyết định <b>hiển thị gì</b> bằng cách gọi
 * {@link #getCountdownDisplay()} — tách biệt hoàn toàn hai mối quan tâm.</p>
 *
 * <ul>
 *   <li>{@link CountdownLight}  — luôn hiển thị đếm ngược.</li>
 *   <li>{@link NoCountdownLight} — không bao giờ hiển thị số giây.</li>
 *   <li>{@link TenSecLight}     — chỉ hiển thị khi còn ≤ 10 giây.</li>
 * </ul>
 */
public abstract class TrafficLight {

    protected final int redTime;
    protected final int greenTime;
    protected final int yellowTime;

    protected double worldX;
    protected double worldY;

    protected TrafficLightState currentState;

    /**
     * Số giây còn lại của trạng thái hiện tại.
     * Luôn đếm đúng thực tế — không bị bóp méo vì mục đích hiển thị.
     */
    protected int secondsRemaining;

    /**
     * Tích lũy thời gian lẻ giữa các lần tick (giây).
     */
    private double timeAccumulator = 0;

    public TrafficLight(int redTime, int greenTime, int yellowTime) {
        this.redTime          = redTime;
        this.greenTime        = greenTime;
        this.yellowTime       = yellowTime;
        this.currentState     = TrafficLightState.RED;
        this.secondsRemaining = redTime;
    }

    // =========================================================
    // Cập nhật mỗi tick (Phase 1 của simulation)
    // =========================================================

    /**
     * Cập nhật trạng thái đèn dựa trên thời gian trôi qua.
     * @param dt Thời gian trôi qua kể từ tick trước (giây).
     */
    public void tick(double dt) {
        timeAccumulator += dt;
        while (timeAccumulator >= 1.0) {
            timeAccumulator -= 1.0;
            onSecondPassed();
        }
    }

    /**
     * Logic thực hiện khi tròn 1 giây trôi qua.
     */
    protected void onSecondPassed() {
        if (secondsRemaining > 1) {
            secondsRemaining--;
        } else {
            switchTo(nextState(currentState));
        }
    }

    // =========================================================
    // Điều khiển thủ công (người dùng click vào đèn)
    // =========================================================

    /**
     * Chuyển ngay sang trạng thái tiếp theo, bất kể còn bao nhiêu giây.
     * Dùng khi người dùng click vào đèn ở chế độ thủ công.
     */
    public void forceNextState() {
        switchTo(nextState(currentState));
        timeAccumulator = 0; // Reset bộ đếm khi ép chuyển trạng thái
    }

    // =========================================================
    // Hiển thị — Renderer gọi để biết cần vẽ gì
    // =========================================================

    /**
     * Trả về số giây cần hiển thị trên mặt đèn.
     *
     * <p>Mỗi subclass tự quyết định có hiển thị không và hiển thị khi nào.
     * Renderer KHÔNG cần biết đèn thuộc loại nào — chỉ cần gọi method này.</p>
     *
     * @return {@code Optional.of(giây)} nếu cần hiển thị,
     *         {@code Optional.empty()} nếu không hiển thị số giây.
     */
    public abstract Optional<Integer> getCountdownDisplay();

    // =========================================================
    // Getters
    // =========================================================

    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }

    public void setWorldPosition(double x, double y) {
        this.worldX = x;
        this.worldY = y;
    }

    public TrafficLightState getCurrentState()  { return currentState; }

    /** Số giây còn lại thực tế — dùng cho logic, không phải hiển thị. */
    public int getSecondsRemaining()            { return secondsRemaining; }

    // =========================================================
    // Helper — dùng bởi subclass
    // =========================================================

    /**
     * Chuyển sang trạng thái {@code next} và reset bộ đếm.
     */
    protected void switchTo(TrafficLightState next) {
        this.currentState     = next;
        this.secondsRemaining = getDurationForState(next);
    }

    protected int getDurationForState(TrafficLightState state) {
        switch (state) {
            case RED:    return redTime;
            case GREEN:  return greenTime;
            case YELLOW: return yellowTime;
            default: throw new IllegalArgumentException("Unknown state: " + state);
        }
    }

    protected TrafficLightState nextState(TrafficLightState state) {
        switch (state) {
            case RED:    return TrafficLightState.GREEN;
            case GREEN:  return TrafficLightState.YELLOW;
            case YELLOW: return TrafficLightState.RED;
            default: throw new IllegalArgumentException("Unknown state: " + state);
        }
    }
}
