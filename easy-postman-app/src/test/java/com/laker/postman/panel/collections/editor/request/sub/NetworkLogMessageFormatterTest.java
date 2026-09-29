package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.util.I18nUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class NetworkLogMessageFormatterTest {
    private boolean originalChinese;

    @BeforeMethod
    public void useChineseLocale() {
        originalChinese = I18nUtil.isChinese();
        I18nUtil.setLocale("zh");
    }

    @AfterMethod
    public void restoreLocale() {
        I18nUtil.setLocale(originalChinese ? "zh" : "en");
    }

    @Test
    public void shouldLocalizeStageDisplayNameWithoutChangingTechnicalName() {
        assertEquals(NetworkLogStage.CALL_START.getStageName(), "RequestStart");
        assertEquals(NetworkLogStage.CALL_START.getDisplayName(), "请求开始");
    }

    @Test
    public void shouldLocalizeFollowUpDecisionAndKeepRequestDetails() {
        String formatted = NetworkLogMessageFormatter.format(
                NetworkLogEventStage.FOLLOW_UP_DECISION,
                "Follow-up: false, response: 200, next: none");

        assertEquals(formatted, "是否需要后续请求：否，响应码：200，下一请求：无");
    }

    @Test
    public void shouldLocalizeTlsLabelsWithoutChangingCertificateValues() {
        String formatted = NetworkLogMessageFormatter.format(
                NetworkLogEventStage.SECURE_CONNECT_END,
                "SSL connection using TLS_1_2\n"
                        + "Server certificate:\n"
                        + " subject: CN=example.test\n"
                        + "SSL certificate verify ok.\n");

        assertTrue(formatted.contains("SSL 连接：TLS_1_2"));
        assertTrue(formatted.contains("服务器证书："));
        assertTrue(formatted.contains("主题： CN=example.test"));
        assertTrue(formatted.contains("SSL 证书校验通过。"));
    }
}
