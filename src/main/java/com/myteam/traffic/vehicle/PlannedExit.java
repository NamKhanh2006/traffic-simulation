package com.myteam.traffic.vehicle;

/**
 * Nhánh ra dự kiến tại giao lộ tiếp theo.
 * Driver gán qua {@link Vehicle#setPlannedExit(PlannedExit)}; controller vào path khi {@code t >= 1}.
 */
public enum PlannedExit {
    NONE,
    /** Chọn ngẫu nhiên một nhánh ra hợp lệ tại giao lộ. */
    RANDOM,
    STRAIGHT,
    LEFT,
    RIGHT
}
