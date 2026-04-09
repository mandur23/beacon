package com.example.beacon.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JwtUserIdExtractor {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Authorization: Bearer 토큰에서 userId를 꺼낸다. 없거나 유효하지 않으면 null.
     */
    public Long extractUserId(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearer) || !bearer.startsWith("Bearer ")) {
            return null;
        }
        String token = bearer.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
