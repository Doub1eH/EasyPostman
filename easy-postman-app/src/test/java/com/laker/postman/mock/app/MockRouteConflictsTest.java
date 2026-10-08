package com.laker.postman.mock.app;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;

public class MockRouteConflictsTest {

    @Test
    public void shouldMarkRowsOwnedByDifferentRequestsWithTheSameRoute() {
        List<MockRouteEntry> routes = List.of(
                entry("request-a", "example-a", "get", "/users/", true),
                entry("request-a", "example-b", "GET", "users", true),
                entry("request-b", "example-c", "GET", "/users", true),
                entry("request-c", "", "GET", "/users", false),
                entry("request-d", "example-d", "POST", "/users", true)
        );

        MockRouteConflicts conflicts = MockRouteConflicts.from(routes);

        assertEquals(conflicts.groupCount(), 1);
        assertEquals(conflicts.rowIndexes(), Set.of(0, 1, 2));
    }

    private MockRouteEntry entry(String requestId, String exampleId, String method,
                                 String path, boolean configured) {
        return new MockRouteEntry("collection", "Collection", requestId + ":" + exampleId,
                false, requestId, requestId, exampleId, exampleId, method, path,
                configured ? 200 : 0, 0, configured, false);
    }
}
