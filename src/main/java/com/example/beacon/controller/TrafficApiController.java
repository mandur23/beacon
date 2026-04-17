package com.example.beacon.controller;

import com.example.beacon.entity.FirewallRule;
import com.example.beacon.service.FirewallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/traffic")
@RequiredArgsConstructor
public class TrafficApiController {

    private final FirewallService firewallService;

    /**
     * 특정 IP를 즉시 차단하는 규칙을 생성합니다.
     */
    @PostMapping("/block")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> blockIp(
            @RequestBody Map<String, String> payload,
            Principal principal) {
        
        String ip = payload.get("ip");
        String reason = payload.getOrDefault("reason", "트래픽 모니터링 중 수동 차단");
        String actor = (principal != null) ? principal.getName() : "system";

        if (ip == null || ip.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "차단할 IP가 누락되었습니다."));
        }

    try {
            FirewallRule rule = firewallService.createBlockRuleForIp(ip, reason, actor);
            log.info("[TrafficAPI] IP 수동 차단 실행: ip={}, by={}", ip, actor);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", ip + " 주소가 성공적으로 차단되었습니다.",
                    "ruleId", rule.getId()
            ));
        } catch (Exception e) {
            log.error("[TrafficAPI] IP 차단 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "차단 처리 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }

    /**
     * 외부 IP의 소유주(ISP/ORG) 정보를 조회합니다.
     */
    @GetMapping("/ip-info/{ip}")
    public ResponseEntity<Map<String, Object>> lookupIpInfo(@PathVariable String ip) {
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        
        // 1순위: ip-api.com
        try {
            String url = "http://ip-api.com/json/" + ip + "?fields=status,message,country,city,isp,org,as";
            Map<String, Object> details = restTemplate.getForObject(url, Map.class);
            if (details != null && "success".equals(details.get("status"))) {
                return ResponseEntity.ok(details);
            }
        } catch (Exception ignored) {}

        // 2순위: ipapi.co (백업)
        try {
            String url = "https://ipapi.co/" + ip + "/json/";
            Map<String, Object> details = restTemplate.getForObject(url, Map.class);
            if (details != null && details.get("error") == null) {
                // 필드명 통일 (org -> org, country_name -> country)
                details.put("status", "success");
                details.put("country", details.get("country_name"));
                return ResponseEntity.ok(details);
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of("status", "fail", "message", "모든 조회 서비스 응답 없음"));
    }
}
