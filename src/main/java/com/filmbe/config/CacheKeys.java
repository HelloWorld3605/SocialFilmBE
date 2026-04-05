package com.filmbe.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String parts(Object... parts) {
        List<String> normalizedParts = new ArrayList<>();
        for (Object part : parts) {
            normalizedParts.add(normalize(part));
        }
        return String.join("::", normalizedParts);
    }

    private static String normalize(Object value) {
        if (value == null) {
            return "_";
        }

        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> normalize(entry.getKey()) + "=" + normalize(entry.getValue()))
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("_");
        }

        return String.valueOf(value);
    }
}
