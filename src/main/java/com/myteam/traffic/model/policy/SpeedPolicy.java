package com.myteam.traffic.model.policy;

import java.util.Objects;

public class SpeedPolicy {
    private final double limit;
    private final double tolerance;

    public SpeedPolicy(double limit, double tolerance) {
        this.limit = Math.max(0, limit);
        this.tolerance = Math.max(0, tolerance);
    }

    public double getLimit() { return limit; }
    public double getEffectiveLimit() { return limit + tolerance; }

    public boolean isViolation(double speed) {
        return speed > getEffectiveLimit();
    }

    // FIX #6: Thêm equals/hashCode để so sánh policy đúng cách
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpeedPolicy other)) return false;
        return Double.compare(limit, other.limit) == 0
                && Double.compare(tolerance, other.tolerance) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit, tolerance);
    }

    // FIX #6: Thêm toString() tiện debug
    @Override
    public String toString() {
        return String.format("SpeedPolicy[limit=%.1f, tolerance=%.1f, effective=%.1f km/h]",
                limit, tolerance, getEffectiveLimit());
    }
}