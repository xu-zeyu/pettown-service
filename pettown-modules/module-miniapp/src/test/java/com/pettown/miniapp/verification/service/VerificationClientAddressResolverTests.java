package com.pettown.miniapp.verification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class VerificationClientAddressResolverTests {
    @Test
    void ignoresHeadersFromUntrustedClients() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Real-IP", "198.51.100.42");
        assertThat(new VerificationClientAddressResolver("192.0.2.1").resolve(request))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void acceptsOnlyNumericAddressesFromConfiguredProxy() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Real-IP", "198.51.100.42");
        assertThat(new VerificationClientAddressResolver("192.0.2.1").resolve(request))
                .isEqualTo("198.51.100.42");
        request.removeHeader("X-Real-IP");
        request.addHeader("X-Real-IP", "attacker.example");
        assertThat(new VerificationClientAddressResolver("192.0.2.1").resolve(request))
                .isEqualTo("192.0.2.1");
    }
}
