package com.pettown.miniapp.verification.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 只接受指定反向代理提供的客户端地址，防止匿名请求伪造来源 IP。 */
@Component
public class VerificationClientAddressResolver {
    private final Set<String> trustedProxies;

    public VerificationClientAddressResolver(@Value("${pettown.verification.trusted-proxy-ips:}") String trustedProxyIps) {
        trustedProxies = Arrays.stream(trustedProxyIps.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) {
            return remote;
        }
        String candidate = request.getHeader("X-Real-IP");
        if (candidate == null || candidate.length() > 45 || !candidate.matches("[0-9a-fA-F:.]+")) {
            return remote;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException e) {
            return remote;
        }
    }
}
