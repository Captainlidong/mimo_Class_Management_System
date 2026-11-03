package com.classmgmt.web;

import com.classmgmt.config.AppProperties;
import com.classmgmt.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 访问控制：
 * - 未配置口令（app.access-password 为空）：全放行，本机开发模式。
 * - 已配置口令：严格模式，除白名单外所有 /api/** 必须持有效会话
 *   （CMS_SESSION Cookie 或 X-Auth-Token 头），静态页面资源不拦截。
 */
@Component
public class AccessPasswordFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_API = Set.of(
            "/api/health",
            "/api/auth/status",
            "/api/auth/captcha",
            "/api/auth/login",
            "/api/auth/logout"
    );

    private final AppProperties appProperties;
    private final AuthService authService;

    public AccessPasswordFilter(AppProperties appProperties, AuthService authService) {
        this.appProperties = appProperties;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String password = appProperties.getAccessPassword();
        boolean authRequired = password != null && !password.isBlank();
        String path = request.getRequestURI();

        boolean skip = !authRequired
                || !path.startsWith("/api/")
                || PUBLIC_API.contains(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());

        if (skip) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authService.isValid(tokenOf(request))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    "{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String tokenOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AuthService.COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return request.getHeader(AuthService.TOKEN_HEADER);
    }
}
