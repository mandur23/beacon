package com.example.beacon.service;

import com.example.beacon.entity.TrafficLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoggingService {
    
    private final ObjectMapper objectMapper;
    
    public void logRequest(String method, String uri, String queryString, String ipAddress, 
                          String userAgent, int statusCode, long duration, String username, String error) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("method", method);
            logData.put("uri", uri);
            logData.put("queryString", queryString);
            logData.put("ipAddress", ipAddress);
            logData.put("userAgent", userAgent);
            logData.put("statusCode", statusCode);
            logData.put("duration", duration);
            logData.put("username", username);
            if (error != null) {
                logData.put("error", error);
            }
            
            String jsonLog = objectMapper.writeValueAsString(logData);
            log.info("REQUEST_LOG: {}", jsonLog);
            
        } catch (Exception e) {
            log.error("Failed to log request", e);
        }
    }
    
    public void logSecurityEvent(String eventType, String severity, String sourceIp, 
                                 String description, Map<String, Object> metadata) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("timestamp", System.currentTimeMillis());
            logData.put("eventType", eventType);
            logData.put("severity", severity);
            logData.put("sourceIp", sourceIp);
            logData.put("description", description);
            logData.put("metadata", metadata);
            
            String jsonLog = objectMapper.writeValueAsString(logData);
            log.warn("SECURITY_EVENT: {}", jsonLog);
            
        } catch (Exception e) {
            log.error("Failed to log security event", e);
        }
    }
}
