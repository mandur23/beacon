package com.example.beacon.interceptor;

import com.example.beacon.entity.UserSession;
import com.example.beacon.service.LoggingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestLoggingInterceptor implements HandlerInterceptor {
    
    private final LoggingService loggingService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        long startTime = (Long) request.getAttribute("startTime");
        long duration = System.currentTimeMillis() - startTime;
        
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        int statusCode = response.getStatus();
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
        
        loggingService.logRequest(
            method, 
            uri, 
            queryString, 
            ipAddress, 
            userAgent, 
            statusCode, 
            duration, 
            username,
            ex != null ? ex.getMessage() : null
        );
        
        log.info("Request: {} {} | IP: {} | Status: {} | Duration: {}ms | User: {}", 
                method, uri, ipAddress, statusCode, duration, username);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        
        return request.getRemoteAddr();
    }
}
