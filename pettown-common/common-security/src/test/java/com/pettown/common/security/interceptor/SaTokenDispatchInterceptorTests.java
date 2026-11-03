package com.pettown.common.security.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.servlet.DispatcherType;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class SaTokenDispatchInterceptorTests {
    @ParameterizedTest
    @EnumSource(value = DispatcherType.class, names = {"ASYNC", "ERROR"})
    void skipsAuthenticationOnlyForInternalAsyncAndErrorDispatch(DispatcherType type) throws Exception {
        assertAuthentication(type, false);
    }

    @ParameterizedTest
    @EnumSource(value = DispatcherType.class, names = {"REQUEST", "FORWARD", "INCLUDE"})
    void authenticatesOtherDispatches(DispatcherType type) throws Exception {
        assertAuthentication(type, true);
    }

    @Test
    void saIgnoreOnMethodOrClassSkipsTheAuthenticationCallback() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        var interceptor = new SaTokenDispatchInterceptor(handler -> called.set(true));
        for (Object controller : new Object[]{new PublicMethodController(), new PublicController()}) {
            var handler = new HandlerMethod(controller, controller.getClass().getMethod("publicEndpoint"));
            assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler))
                    .isTrue();
            assertThat(called).isFalse();
        }
    }

    private void assertAuthentication(DispatcherType type, boolean expected) throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        var interceptor = new SaTokenDispatchInterceptor(handler -> called.set(true));
        var request = new MockHttpServletRequest();
        request.setDispatcherType(type);
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(called.get()).isEqualTo(expected);
    }

    static class PublicMethodController {
        @SaIgnore
        public void publicEndpoint() {}
    }

    @SaIgnore
    static class PublicController {
        public void publicEndpoint() {}
    }
}
