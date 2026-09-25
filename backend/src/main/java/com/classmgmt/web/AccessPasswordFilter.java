package com.classmgmt.web;

import com.classmgmt.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AccessPasswordFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Access-Password";

    private final AppProperties appProperties;

    public AccessPasswordFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String password = appProperties.getAccessPassword();
        String path = request.getRequestURI();
        boolean api = path.startsWith("/api/");
        boolean skip = !api
                || path.equals("/api/health")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || password == null
                || password.isBlank();

        if (skip) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER);
        if (!password.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    "{\"code\":401,\"message\":\"访问口令错误或未提供\",\"data\":null}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
