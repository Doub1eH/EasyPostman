package com.laker.postman.mock.app;

import com.laker.postman.mock.model.MockServerDefinition;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MockNetworkAddressResolver {

    public String accessUrl(MockServerDefinition definition, int port) {
        String configuredHost = definition == null ? null : definition.getHost();
        String accessHost = MockServerDefinition.ALL_INTERFACES_HOST.equals(configuredHost)
                ? MockServerDefinition.LOOPBACK_HOST
                : normalizeHost(configuredHost);
        return "http://" + accessHost + ":" + port;
    }

    private String normalizeHost(String host) {
        return host == null || host.isBlank() ? MockServerDefinition.LOOPBACK_HOST : host.trim();
    }
}
