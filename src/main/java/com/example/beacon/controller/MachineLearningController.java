package com.example.beacon.controller;

import com.example.beacon.dto.AnomalyDetectionRequest;
import com.example.beacon.dto.AnomalyDetectionResponse;
import com.example.beacon.entity.TrafficLog;
import com.example.beacon.service.TrafficAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MachineLearningController {
    
    @Value("${ml.python.service.url:http://localhost:5000}")
    private String pythonServiceUrl;
    
    private final TrafficAnalysisService trafficAnalysisService;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @PostMapping("/detect-anomaly")
    public ResponseEntity<AnomalyDetectionResponse> detectAnomaly(@RequestBody AnomalyDetectionRequest request) {
        try {
            String url = pythonServiceUrl + "/api/detect-anomaly";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body != null) {
                AnomalyDetectionResponse result = AnomalyDetectionResponse.builder()
                        .isAnomaly(parseBoolean(body.get("isAnomaly")))
                        .anomalyScore(parseDouble(body.get("anomalyScore")))
                        .anomalyReason(parseString(body.get("anomalyReason"), "No anomaly reason from ML service"))
                        .build();
                
                if (request.getTrafficLogId() != null && result.getIsAnomaly()) {
                    trafficAnalysisService.markAsAnomaly(
                        request.getTrafficLogId(), 
                        result.getAnomalyScore(), 
                        result.getAnomalyReason()
                    );
                }
                
                return ResponseEntity.ok(result);
            }
            
            return ResponseEntity.status(503).body(AnomalyDetectionResponse.builder()
                    .isAnomaly(false)
                    .anomalyScore(0.0)
                    .anomalyReason("No response body from ML service")
                    .build());
            
        } catch (Exception e) {
            return ResponseEntity.status(503).body(AnomalyDetectionResponse.builder()
                    .isAnomaly(false)
                    .anomalyScore(0.0)
                    .anomalyReason("ML service unavailable: " + e.getMessage())
                    .build());
        }
    }
    
    @PostMapping("/batch-detect")
    public ResponseEntity<List<AnomalyDetectionResponse>> batchDetectAnomalies(
            @RequestBody List<AnomalyDetectionRequest> requests) {
        
        try {
            String url = pythonServiceUrl + "/api/batch-detect-anomaly";
            ResponseEntity<List> response = restTemplate.postForEntity(url, requests, List.class);
            List<?> body = response.getBody();
            if (body == null) {
                return ResponseEntity.status(503).body(null);
            }
            @SuppressWarnings("unchecked")
            List<AnomalyDetectionResponse> typed = (List<AnomalyDetectionResponse>) body;
            return ResponseEntity.ok(typed);
            
        } catch (Exception e) {
            return ResponseEntity.status(503).body(null);
        }
    }
    
    @PostMapping("/train-model")
    public ResponseEntity<Map<String, Object>> trainModel() {
        try {
            String url = pythonServiceUrl + "/api/train-model";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return ResponseEntity.status(503).body(Map.of(
                    "success", false,
                    "message", "ML service returned empty response"
                ));
            }
            return ResponseEntity.ok(body);
            
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "success", false,
                "message", "Failed to trigger model training: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/model-status")
    public ResponseEntity<Map<String, Object>> getModelStatus() {
        try {
            String url = pythonServiceUrl + "/api/model-status";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return ResponseEntity.status(503).body(Map.of(
                    "available", false,
                    "message", "ML service returned empty model status"
                ));
            }
            return ResponseEntity.ok(body);
            
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "available", false,
                "message", "ML service unavailable: " + e.getMessage()
            ));
        }
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return false;
    }

    private double parseDouble(Object value) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String parseString(Object value, String defaultValue) {
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        return defaultValue;
    }
}
