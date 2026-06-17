package com.myteam.traffic.controller;

import com.myteam.traffic.behavior.*;
import com.myteam.traffic.model.infrastructure.*;
import com.myteam.traffic.model.infrastructure.intersection.Intersection;
import com.myteam.traffic.vehicle.Vehicle;
import com.myteam.traffic.vehicle.VehicleType;

import java.util.*;

public class VehicleSpawner {

    // Khoảng cách an toàn để không đẻ xe đè lên nhau (chỉ cần 15m là đủ an toàn, 150m là quá lớn gây lỗi)
    private static final double MIN_SPAWN_CLEARANCE = 15.0;
    public static final double FADE_DURATION = 1.5;

    private final TrafficController controller;
    private final RoadNetwork network;
    private final Random random = new Random();

    private boolean enabled = false;
    private double spawnIntervalSeconds = 0.5;
    private double accumulator = 0.0;

    private final Map<VehicleType, Double> vehicleRatios = new LinkedHashMap<>();
    private double aggressiveRatio = 0.3;

    private final Map<Vehicle, Double> spawnAlphaMap = new WeakHashMap<>();

    public VehicleSpawner(TrafficController controller, RoadNetwork network) {
        this.controller = controller;
        this.network = network;
        vehicleRatios.put(VehicleType.CAR, 0.5);
        vehicleRatios.put(VehicleType.MOTORBIKE, 0.4);
        vehicleRatios.put(VehicleType.BICYCLE, 0.1);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setSpawnRatePerMinute(double rate) {
        this.spawnIntervalSeconds = rate <= 0 ? Double.MAX_VALUE : 60.0 / rate;
    }

    public void setVehicleRatio(VehicleType type, double ratio) {
        vehicleRatios.put(type, Math.max(0, ratio));
    }
    public double getVehicleRatio(VehicleType type) { return vehicleRatios.getOrDefault(type, 0.0); }
    public void setAggressiveRatio(double ratio) { this.aggressiveRatio = Math.max(0, Math.min(1, ratio)); }
    public double getSpawnAlpha(Vehicle v) { return spawnAlphaMap.getOrDefault(v, 1.0); }

    public void tick(double deltaTime) {
        for (Map.Entry<Vehicle, Double> e : new ArrayList<>(spawnAlphaMap.entrySet())) {
            double alpha = e.getValue() + deltaTime / FADE_DURATION;
            if (alpha >= 1.0) spawnAlphaMap.remove(e.getKey());
            else spawnAlphaMap.put(e.getKey(), alpha);
        }

        if (!enabled) return;
        accumulator += deltaTime;
        while (accumulator >= spawnIntervalSeconds) {
            accumulator -= spawnIntervalSeconds;
            trySpawnRandom();
        }
    }

    public boolean trySpawnRandom() {
        List<SpawnCandidate> candidates = getEdgeLanes();

        // Fallback: Nếu bản đồ là 1 vòng đua khép kín không có ngõ cụt thì lấy bừa 1 đường
        if (candidates.isEmpty()) candidates = getFallbackLanes();
        if (candidates.isEmpty()) return false;

        Collections.shuffle(candidates, random);

        for (SpawnCandidate cand : candidates) {
            if (!isClearToSpawn(cand.seg, cand.lane, cand.isForward)) continue;

            VehicleType type = pickWeightedVehicleType();
            DriverBehavior behavior;
            if (type == VehicleType.AMBULANCE || type == VehicleType.FIRETRUCK) {
                behavior = new EmergencyDriver();
            } else {
                behavior = (random.nextDouble() < aggressiveRatio)
                    ? new AggressiveDriver() : new NormalDriver();
            }

            Vehicle v = doSpawn(cand.seg, cand.lane, cand.isForward, behavior, type);
            if (v != null) {
                spawnAlphaMap.put(v, 0.0);
                return true;
            }
        }
        return false;
    }

    public boolean spawnManual(VehicleType type, boolean aggressive) {
        // 1. Thử rìa bản đồ trước
        List<SpawnCandidate> candidates = getEdgeLanes();
        Collections.shuffle(candidates, random);

        for (SpawnCandidate cand : candidates) {
            if (!isClearToSpawn(cand.seg, cand.lane, cand.isForward)) continue;

            DriverBehavior behavior;
            if (type == VehicleType.AMBULANCE || type == VehicleType.FIRETRUCK) {
                behavior = new EmergencyDriver();
            } else {
                behavior = aggressive ? new AggressiveDriver() : new NormalDriver();
            }
            Vehicle v = doSpawn(cand.seg, cand.lane, cand.isForward, behavior, type);
            if (v != null) {
                spawnAlphaMap.put(v, 0.0);
                return true;
            }
        }

        // 2. Nếu rìa bản đồ bị tắc, thử vị trí ngẫu nhiên trên đường
        List<RoadSegment> segments = new ArrayList<>(network.getSegments());
        Collections.shuffle(segments, random);
        for (RoadSegment seg : segments) {
            if (seg.isConnector() || seg.getLength() < 40.0) continue;
            for (Lane lane : seg.getLanes()) {
                for (int i = 0; i < 3; i++) {
                    double randomT = 0.1 + random.nextDouble() * 0.8;
                    boolean clear = true;
                    try {
                        double[] spawnPos = seg.getPositionOnLane(lane.getIndex(), randomT);
                        for (Vehicle other : controller.getVehicles()) {
                            if (Math.hypot(other.getX() - spawnPos[0], other.getY() - spawnPos[1]) < MIN_SPAWN_CLEARANCE) {
                                clear = false; break;
                            }
                        }
                    } catch (Exception e) { clear = false; }

                    if (clear) {
                        DriverBehavior behavior = (type == VehicleType.AMBULANCE || type == VehicleType.FIRETRUCK) 
                                ? new EmergencyDriver() : (aggressive ? new AggressiveDriver() : new NormalDriver());
                        try {
                            Vehicle v = controller.createVehicleByType(type, behavior);
                            v.placeOnSegment(seg, lane, randomT);
                            v.setSpeed(20.0);
                            controller.addVehicle(v);
                            spawnAlphaMap.put(v, 0.0);
                            return true;
                        } catch (Exception e) {}
                    }
                }
            }
        }

        return false;
    }

    // --- Internals ---
    private static class SpawnCandidate {
        RoadSegment seg; Lane lane; boolean isForward;
        SpawnCandidate(RoadSegment s, Lane l, boolean fwd) { seg = s; lane = l; isForward = fwd; }
    }

    /**
     * Quét tọa độ VẬT LÝ để tìm đúng ngõ cụt.
     * Ngõ cụt là điểm không nằm trong Giao lộ nào, và không chạm vào bất kỳ đường nào khác.
     */
    private boolean isDeadEnd(double x, double y, RoadSegment excludeSeg) {
        // 1. Kiểm tra xem điểm này có nằm trong lòng bất kỳ nút giao/vòng xuyến nào không
        for (Intersection inter : network.getIntersections()) {
            double radius = 40.0;
            if (inter.getRenderData() != null) {
                radius = inter.getRenderData().radius;
            }
            // Nếu điểm x, y bị nút giao nuốt trọn -> Khỏi spawn
            if (Math.hypot(x - inter.getCenterX(), y - inter.getCenterY()) <= radius + 5.0) {
                return false;
            }
        }

        // 2. Kiểm tra xem điểm này có đang nối thẳng vào cái đường nào khác không
        for (RoadSegment seg : network.getSegments()) {
            if (seg == excludeSeg) continue;
            if (Math.hypot(x - seg.getStartX(), y - seg.getStartY()) < 2.0) return false;
            if (Math.hypot(x - seg.getEndX(), y - seg.getEndY()) < 2.0) return false;
        }

        return true; // Nếu qua được hết -> Chắc chắn là ngõ cụt ở rìa bản đồ!
    }

    private List<SpawnCandidate> getEdgeLanes() {
        List<SpawnCandidate> result = new ArrayList<>();
        for (RoadSegment seg : network.getSegments()) {
            if (seg.isConnector() || seg.getLength() < 50.0) continue;

            boolean startIsDeadEnd = isDeadEnd(seg.getStartX(), seg.getStartY(), seg);
            boolean endIsDeadEnd   = isDeadEnd(seg.getEndX(), seg.getEndY(), seg);

            for (Lane lane : seg.getLanes()) {
                // Làn xuôi (FORWARD) bắt đầu từ đầu START của đường
                if (lane.getDirection() == Lane.Direction.FORWARD && startIsDeadEnd) {
                    result.add(new SpawnCandidate(seg, lane, true));
                }
                // Làn ngược (BACKWARD) bắt đầu từ đầu END của đường
                else if (lane.getDirection() == Lane.Direction.BACKWARD && endIsDeadEnd) {
                    result.add(new SpawnCandidate(seg, lane, false));
                }
            }
        }
        return result;
    }

    private List<SpawnCandidate> getFallbackLanes() {
        List<SpawnCandidate> result = new ArrayList<>();
        for (RoadSegment seg : network.getSegments()) {
            if (!seg.isConnector() && seg.getLength() >= 80.0) {
                for (Lane lane : seg.getLanes()) {
                    result.add(new SpawnCandidate(seg, lane, lane.getDirection() == Lane.Direction.FORWARD));
                }
            }
        }
        return result;
    }

    private boolean isClearToSpawn(RoadSegment seg, Lane lane, boolean isForward) {
        double spawnT = isForward ? 0.0 : 1.0;
        double[] spawnPos;
        try {
            spawnPos = seg.getPositionOnLane(lane.getIndex(), spawnT);
        } catch (Exception e) { return false; }

        double sx = spawnPos[0], sy = spawnPos[1];

        // Kiểm tra xem vị trí định đẻ xe có bị xe nào khác đứng đè lên không
        for (Vehicle v : controller.getVehicles()) {
            double dist = Math.hypot(v.getX() - sx, v.getY() - sy);
            if (dist < MIN_SPAWN_CLEARANCE) return false;
        }
        return true;
    }

    private Vehicle doSpawn(RoadSegment seg, Lane lane, boolean isForward, DriverBehavior behavior, VehicleType type) {
        try {
            double startT = isForward ? 0.0 : 1.0;
            Vehicle v = controller.createVehicleByType(type, behavior);
            v.placeOnSegment(seg, lane, startT);
            v.setSpeed(20.0); // Chạy vào với vận tốc 20
            controller.addVehicle(v);
            return v;
        } catch (Exception e) {
            System.err.println("Lỗi spawn xe: " + e.getMessage());
            return null;
        }
    }

    private VehicleType pickWeightedVehicleType() {
        double total = vehicleRatios.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return VehicleType.CAR;
        double r = random.nextDouble() * total;
        double cum = 0;
        for (var e : vehicleRatios.entrySet()) {
            cum += e.getValue();
            if (r <= cum) return e.getKey();
        }
        return VehicleType.CAR;
    }
}
