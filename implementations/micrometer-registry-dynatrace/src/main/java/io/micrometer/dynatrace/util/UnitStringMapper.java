package io.micrometer.dynatrace.util;

import java.util.HashMap;
import java.util.Map;

public class UnitStringMapper {
    private static final Map<String, String> mappings = getDefaultUnitMappings();

    private UnitStringMapper() {
    }

    public static String mapUnitIfNeeded(String unit) {
        if (unit == null) {
            return null;
        }

        if (mappings.containsKey(unit)) {
            return mappings.get(unit);
        }

        return unit;
    }

    private static Map<String, String> getDefaultUnitMappings() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("nanoseconds", "ns");
        mapping.put("nanosecond", "ns");
        mapping.put("milliseconds", "ms");
        mapping.put("millisecond", "ms");
        mapping.put("seconds", "s");
        mapping.put("second", "s");

        return mapping;
    }
}
