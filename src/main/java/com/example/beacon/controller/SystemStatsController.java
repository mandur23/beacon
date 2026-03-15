package com.example.beacon.controller;

import com.example.beacon.service.NetworkStatsService;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemStatsController {

    private final NetworkStatsService networkStatsService;

    public SystemStatsController(NetworkStatsService networkStatsService) {
        this.networkStatsService = networkStatsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Integer>> getStats() {
        OperatingSystemMXBean osBean =
                ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        double cpuLoad = osBean.getCpuLoad();
        int cpu = cpuLoad >= 0 ? (int) Math.round(cpuLoad * 100) : 0;

        long totalMem = osBean.getTotalMemorySize();
        long freeMem  = osBean.getFreeMemorySize();
        int mem = totalMem > 0 ? (int) Math.round((1.0 - (double) freeMem / totalMem) * 100) : 0;

        int net = networkStatsService.getUsagePercent();

        return ResponseEntity.ok(Map.of("cpu", cpu, "mem", mem, "net", net));
    }
}
