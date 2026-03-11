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
                        .isAnomaly((Boolean) body.get("isAnomaly"))
                        .anomalyScore(((Number) body.get("anomalyScore")).doubleValue())
                        .anomalyReason((String) body.get("anomalyReason"))
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
            
            return ResponseEntity.ok(AnomalyDetectionResponse.builder()
                    .isAnomaly(false)
                    .anomalyScore(0.0)
                    .anomalyReason("No response from ML service")
                    .build());
            
        } catch (Exception e) {
            return ResponseEntity.ok(AnomalyDetectionResponse.builder()
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
            
            return ResponseEntity.ok(response.getBody());
            
        } catch (Exception e) {
            return ResponseEntity.status(503).body(null);
        }
    }
    
    @PostMapping("/train-model")
    public ResponseEntity<Map<String, Object>> trainModel() {
        try {
            String url = pythonServiceUrl + "/api/train-model";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
            
            return ResponseEntity.ok(response.getBody());
            
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
            
            return ResponseEntity.ok(response.getBody());
            
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "available", false,
                "message", "ML service unavailable: " + e.getMessage()
            ));
        }
    }
}
