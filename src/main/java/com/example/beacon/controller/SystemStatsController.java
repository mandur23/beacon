package com.example.beacon.controller;

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

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Integer>> getStats() {
        OperatingSystemMXBean osBean =
                ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        double cpuLoad = osBean.getCpuLoad();
        int cpu = cpuLoad >= 0 ? (int) Math.round(cpuLoad * 100) : 0;

        long totalMem = osBean.getTotalMemorySize();
        long freeMem = osBean.getFreeMemorySize();
        int mem = totalMem > 0 ? (int) Math.round((1.0 - (double) freeMem / totalMem) * 100) : 0;

        int net = Math.min(100, (cpu / 3) + (int) (Math.random() * 15) + 5);

        return ResponseEntity.ok(Map.of("cpu", cpu, "mem", mem, "net", net));
    }
}
