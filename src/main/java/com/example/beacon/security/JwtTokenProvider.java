package com.example.beacon.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// 임시 토큰(MFA 대기)은 5분 유효, mfa_pending=true 클레임으로 구분한다.

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretString;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    @PostConstruct
    public void validateSecret() {
        if (secretString == null || secretString.isBlank()) {
            throw new IllegalStateException("jwt.secret 설정값이 없습니다. application.yaml 또는 환경변수를 확인하세요.");
        }
        if (secretString.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret은 최소 256비트(32바이트) 이상이어야 합니다.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(String username, Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.getSubject();
    }
    
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.get("userId", Long.class);
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String generateTempToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("mfa_pending", true)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 5 * 60 * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTempToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Boolean.TRUE.equals(claims.get("mfa_pending", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromTempToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
