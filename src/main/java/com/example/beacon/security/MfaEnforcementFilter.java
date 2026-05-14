package com.example.beacon.security;

import com.example.beacon.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class MfaEnforcementFilter extends OncePerRequestFilter {

    public static final String MFA_VERIFIED_SESSION_KEY = "MFA_VERIFIED";
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        // JWT 기반 API 인증은 세션이 없을 수 있으므로 별도 MFA 흐름(임시 토큰 -> OTP 검증)을 그대로 사용한다.
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean mfaEnabled = userRepository.findByUsername(authentication.getName())
                .map(user -> Boolean.TRUE.equals(user.getMfaEnabled()))
                .orElse(false);
        if (!mfaEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean mfaVerified = session != null && Boolean.TRUE.equals(session.getAttribute(MFA_VERIFIED_SESSION_KEY));
        if (mfaVerified) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isAllowedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"MFA verification required\"}");
            return;
        }

        response.sendRedirect("/mfa-challenge");
    }

    private boolean isAllowedPath(String path) {
        return "/mfa-challenge".equals(path)
                || "/mfa/verify".equals(path)
                || "/logout".equals(path)
                || "/error".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || "/favicon.ico".equals(path);
    }
}
