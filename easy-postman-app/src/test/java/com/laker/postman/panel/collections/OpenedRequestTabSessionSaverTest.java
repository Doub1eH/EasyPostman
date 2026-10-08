package com.laker.postman.panel.collections;

import com.laker.postman.request.model.HttpRequestItem;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

public class OpenedRequestTabSessionSaverTest {

    @Test
    public void shouldPreservePendingTabsAndPreferCurrentVersionOfSameRequest() {
        HttpRequestItem pending = request("old", "Previously open");
        HttpRequestItem stale = request("same", "Old version");
        HttpRequestItem updated = request("same", "Current version");
        HttpRequestItem newlyOpened = request("new", "Newly opened");

        List<HttpRequestItem> merged = OpenedRequestTabSessionSaver.mergeWithPendingSession(
                List.of(pending, stale), List.of(updated, newlyOpened));

        assertEquals(merged.size(), 3);
        assertSame(merged.get(0), pending);
        assertSame(merged.get(1), updated);
        assertSame(merged.get(2), newlyOpened);
    }

    private static HttpRequestItem request(String id, String name) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(id);
        item.setName(name);
        return item;
    }
}
