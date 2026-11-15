package com.classmgmt.web;

import com.classmgmt.config.AppProperties;
import com.classmgmt.service.AuthService;
import com.classmgmt.service.CaptchaService;
import com.classmgmt.service.LoginAttemptService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppProperties appProperties;
    private final AuthService authService;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AppProperties appProperties,
                          AuthService authService,
                          CaptchaService captchaService,
                          LoginAttemptService loginAttemptService) {
        this.appProperties = appProperties;
        this.authService = authService;
        this.captchaService = captchaService;
        this.loginAttemptService = loginAttemptService;
    }

    public record LoginRequest(String username, String password, String captchaId, String captchaCode) {
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
        boolean required = authRequired();
        boolean loggedIn = !required || authService.isValid(tokenOf(request));
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("authRequired", required);
        data.put("loggedIn", loggedIn);
        data.put("username", loggedIn ? appProperties.getAccessUsername() : "");
        // 会话滑动续期发生在 isValid 内，校验通过即代表过期时间为 now + TTL
        if (loggedIn && required) {
            data.put("sessionExpiresAt", java.time.Instant.now().plus(AuthService.SESSION_TTL));
        }
        return ApiResponse.ok(data);
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaService.Captcha> captcha() {
        if (!authRequired()) {
            throw GlobalExceptionHandler.badRequest("当前未启用口令，无需登录");
        }
        return ApiResponse.ok(captchaService.generate());
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest body,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        if (!authRequired()) {
            throw GlobalExceptionHandler.badRequest("当前未启用口令，无需登录");
        }
        String ip = clientIp(request);
        if (loginAttemptService.isLocked(ip)) {
            throw new GlobalExceptionHandler.ApiException(429, HttpStatus.TOO_MANY_REQUESTS,
                    "尝试次数过多，账号已临时锁定，请约 " + Math.round(loginAttemptService.lockedForSeconds(ip) / 60.0) + " 分钟后再试");
        }
        if (!captchaService.verify(body.captchaId(), body.captchaCode())) {
            throw GlobalExceptionHandler.badRequest("验证码错误，请重试");
        }
        boolean userOk = constEq(appProperties.getAccessUsername(), body.username());
        boolean passOk = constEq(appProperties.getAccessPassword(), body.password());
        if (!userOk || !passOk) {
            loginAttemptService.recordFailure(ip);
            throw GlobalExceptionHandler.badRequest("用户名或密码错误");
        }
        loginAttemptService.clear(ip);
        String token = authService.create();
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(token, AuthService.SESSION_TTL).toString());
        return ApiResponse.ok(Map.of(
                "loggedIn", true,
                "username", body.username() == null ? "" : body.username().trim()
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.invalidate(tokenOf(request));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
        return ApiResponse.ok(Map.of("loggedIn", false));
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthService.COOKIE_NAME, value)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private boolean authRequired() {
        String pw = appProperties.getAccessPassword();
        return pw != null && !pw.isBlank();
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

    private boolean constEq(String expected, String provided) {
        String e = expected == null ? "" : expected;
        String p = provided == null ? "" : provided;
        return MessageDigest.isEqual(
                e.getBytes(StandardCharsets.UTF_8),
                p.getBytes(StandardCharsets.UTF_8));
    }

    /** 反向代理场景取真实客户端 IP：优先 X-Forwarded-For 首段（由自己的 Nginx 写入）。 */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = comma > 0 ? xff.substring(0, comma) : xff;
            return first.trim();
        }
        return request.getRemoteAddr();
    }
}
