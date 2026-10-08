package com.laker.postman.mock.app;

import cn.hutool.json.JSONUtil;
import com.laker.postman.mock.model.MockServerDefinition;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class MockServerLegacyConfigTest {

    @Test
    public void shouldReadOldAutoStartSettingWithoutWritingItBack() {
        List<MockServerDefinition> definitions = JSONUtil.toList(
                JSONUtil.parseArray("[{\"id\":\"server-1\",\"name\":\"Legacy\",\"port\":3001,\"autoStart\":true}]"),
                MockServerDefinition.class);

        assertEquals(definitions.size(), 1);
        assertEquals(definitions.get(0).getName(), "Legacy");
        assertEquals(definitions.get(0).getPort(), 3001);
        assertFalse(JSONUtil.toJsonStr(definitions).contains("autoStart"));
    }
}
