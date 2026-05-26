package com.myteam.traffic.sign;

/**
 * Các loại biển báo giao thông trong simulation.
 *
 * <p>Chia thành 3 nhóm theo chức năng:</p>
 * <ul>
 *   <li><b>Biển cấm</b> — cấm một hành động hoặc loại xe.</li>
 *   <li><b>Biển hiệu lệnh</b> — bắt buộc thực hiện một hành động.</li>
 *   <li><b>Biển giới hạn / thông tin</b> — giới hạn thông số hoặc cung cấp thông tin.</li>
 * </ul>
 */
public enum SignType {

    // =========================================================
    // Biển cấm (Prohibitory signs)
    // =========================================================

    /** Cấm đi vào. */
    NO_ENTRY,

    /** Cấm vượt xe. */
    NO_OVERTAKING,

    /** Cấm bóp còi. */
    NO_HORN,

    /** Cấm rẽ trái. */
    NO_LEFT_TURN,

    /** Cấm rẽ phải. */
    NO_RIGHT_TURN,

    /** Cấm quay đầu. */
    NO_U_TURN,

    /** Cấm đỗ xe. */
    NO_PARKING,

    // =========================================================
    // Biển hiệu lệnh (Mandatory signs)
    // =========================================================

    /** Bắt buộc dừng lại và nhường đường. */
    STOP,

    /** Nhường đường cho xe ưu tiên. */
    YIELD,

    /** Chỉ đi thẳng. */
    STRAIGHT_ONLY,

    /** Chỉ rẽ trái. */
    LEFT_TURN_ONLY,

    /** Chỉ rẽ phải. */
    RIGHT_TURN_ONLY,

    /** Đường một chiều. */
    ONE_WAY,

    // =========================================================
    // Biển giới hạn / thông tin (Informational / Limit signs)
    // =========================================================

    /** Giới hạn tốc độ tối đa (kèm giá trị số). */
    SPEED_LIMIT,

    /** Giới hạn chiều cao tối đa. */
    MAX_HEIGHT,

    /** Giới hạn tải trọng tối đa. */
    MAX_WEIGHT,

    /** Giới hạn chiều dài tối đa. */
    MAX_LENGTH,

    /** Khu vực trường học — giảm tốc độ, tăng mức cảnh giác. */
    SCHOOL_ZONE,

    /** Khu vực bệnh viện — cấm còi. */
    HOSPITAL_ZONE,
}