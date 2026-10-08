package com.laker.postman.mock.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Routes with the same method and normalized path owned by different requests. */
public record MockRouteConflicts(int groupCount, Set<Integer> rowIndexes) {

    public static MockRouteConflicts from(List<MockRouteEntry> routes) {
        Map<String, Map<String, List<Integer>>> byRoute = new LinkedHashMap<>();
        for (int index = 0; index < routes.size(); index++) {
            MockRouteEntry route = routes.get(index);
            if (!route.configured()) continue;
            String key = normalizedMethod(route.method()) + " " + normalizedPath(route.path());
            String owner = route.standalone()
                    ? "standalone:" + route.routeId()
                    : route.sourceCollectionId() + ":" + route.requestId();
            byRoute.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(owner, ignored -> new ArrayList<>()).add(index);
        }

        int groups = 0;
        Set<Integer> rows = new LinkedHashSet<>();
        for (Map<String, List<Integer>> owners : byRoute.values()) {
            if (owners.size() < 2) continue;
            groups++;
            owners.values().forEach(rows::addAll);
        }
        return new MockRouteConflicts(groups, Set.copyOf(rows));
    }

    private static String normalizedMethod(String method) {
        return method == null ? "" : method.toUpperCase(Locale.ROOT);
    }

    private static String normalizedPath(String path) {
        String normalized = path == null || path.isBlank() ? "/" : path.trim();
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
