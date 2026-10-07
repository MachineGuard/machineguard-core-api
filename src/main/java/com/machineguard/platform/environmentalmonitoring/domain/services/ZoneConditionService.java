package com.machineguard.platform.environmentalmonitoring.domain.services;

import com.machineguard.platform.environmentalmonitoring.domain.model.*;
import java.util.List;

/** Environmental state is distinct from the zone's administrative ACTIVE/INACTIVE status. */
public final class ZoneConditionService {
    private ZoneConditionService() {}
    public static String evaluate(List<SensorNode> nodes, List<Threshold> thresholds, List<Measurement> latest) {
        if (!nodes.isEmpty() && nodes.stream().noneMatch(n -> n.getStatus() == SensorNodeStatus.ONLINE)) return "OFFLINE";
        if (latest.isEmpty()) return "NO_DATA";
        boolean near = false;
        for (Measurement reading : latest) {
            Threshold threshold = thresholds.stream().filter(t -> t.getEnvironmentalVariable() == reading.getEnvironmentalVariable()).findFirst().orElse(null);
            if (threshold == null) return "NO_DATA";
            if (threshold.isOutOfRange(reading.getMeasuredValue())) return "OUT_OF_RANGE";
            near |= threshold.isNearLimit(reading.getMeasuredValue());
        }
        return near ? "NEAR_LIMIT" : "NORMAL";
    }
}
