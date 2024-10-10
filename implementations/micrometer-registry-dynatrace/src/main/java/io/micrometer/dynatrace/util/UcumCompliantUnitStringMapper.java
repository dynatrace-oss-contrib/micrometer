/*
 * Copyright 2024 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.dynatrace.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A helper that maps certain unit strings to UCUM compliant unit strings.
 * See the <a href=https://ucum.org/ucum>the UCUM specification</a> for more information
 */
public class UcumCompliantUnitStringMapper {
    private static final Map<String, String> mappings = getDefaultUnitMappings();

    private UcumCompliantUnitStringMapper() {
    }

    /**
     * Map a unit string to a UCUM compliant string, if it is defined.
     * @param unit the unit that should be mapped
     * @return The UCUM-compliant string if defined, otherwise returns the string that was initially passed.
     */
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
        mapping.put("microseconds", "us");
        mapping.put("microsecond", "us");
        mapping.put("milliseconds", "ms");
        mapping.put("millisecond", "ms");
        mapping.put("seconds", "s");
        mapping.put("second", "s");

        return mapping;
    }
}
