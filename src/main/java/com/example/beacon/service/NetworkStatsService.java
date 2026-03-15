package com.example.beacon.service;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NIC 누적 바이트 카운터 두 시점의 델타를 이용해
 * 실제 네트워크 사용률(0~100 %)을 계산한다.
 *
 * 링크 속도를 최대 대역폭 기준으로 삼고,
 * 속도를 알 수 없을 때는 1 Gbps를 폴백으로 사용한다.
 */
@Component
public class NetworkStatsService {

    private static final long FALLBACK_SPEED_BITS = 1_000_000_000L; // 1 Gbps

    private final List<NetworkIF> networkIFs;

    private long prevTimestampMs = System.currentTimeMillis();
    private long prevTotalBytes  = -1; // -1 = 아직 베이스라인 없음

    public NetworkStatsService() {
        SystemInfo si = new SystemInfo();
        this.networkIFs = si.getHardware().getNetworkIFs();
    }

    /**
     * 이전 호출 이후의 송수신 바이트 변화량을 링크 속도 대비 백분율로 반환한다.
     * 첫 호출 시에는 베이스라인만 기록하고 0을 반환한다.
     */
    public synchronized int getUsagePercent() {
        long nowMs        = System.currentTimeMillis();
        long nowTotalBytes = 0;
        long totalSpeedBits = 0;

        for (NetworkIF net : networkIFs) {
            net.updateAttributes();
            nowTotalBytes  += net.getBytesSent() + net.getBytesRecv();
            long speed      = net.getSpeed();
            if (speed > 0) {
                totalSpeedBits += speed;
            }
        }

        // 첫 호출 – 베이스라인 기록
        if (prevTotalBytes < 0) {
            prevTotalBytes   = nowTotalBytes;
            prevTimestampMs  = nowMs;
            return 0;
        }

        long deltaMs    = nowMs - prevTimestampMs;
        long deltaBytes = nowTotalBytes - prevTotalBytes;

        prevTimestampMs = nowMs;
        prevTotalBytes  = nowTotalBytes;

        // 시계 역행 또는 카운터 리셋 방어
        if (deltaMs <= 0 || deltaBytes < 0) {
            return 0;
        }

        double bytesPerSec   = deltaBytes * 1_000.0 / deltaMs;
        double maxBytesPerSec = totalSpeedBits > 0
                ? totalSpeedBits / 8.0
                : FALLBACK_SPEED_BITS / 8.0;

        int percent = (int) Math.round(bytesPerSec / maxBytesPerSec * 100.0);
        return Math.min(100, Math.max(0, percent));
    }

    /**
     * 서버의 실제 네트워크 인터페이스 목록을 반환한다.
     * IPv4/IPv6 주소가 없는 인터페이스(루프백 포함)는 제외한다.
     */
    public List<Map<String, Object>> getNetworkInterfaces() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (NetworkIF net : networkIFs) {
            net.updateAttributes();

            String[] ipv4 = net.getIPv4addr();
            String[] ipv6 = net.getIPv6addr();

            // 주소 없는 인터페이스(루프백 등) 제외
            if (ipv4.length == 0 && ipv6.length == 0) continue;

            String ip      = ipv4.length > 0 ? ipv4[0] : ipv6[0];
            long   speed   = net.getSpeed();
            long   errors  = net.getInErrors() + net.getOutErrors();
            long   packets = net.getPacketsSent() + net.getPacketsRecv();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name",    net.getName());
            m.put("ip",      ip);
            m.put("speed",   speed > 0 ? formatBits(speed) : "N/A");
            m.put("tx",      formatBytes(net.getBytesSent()));
            m.put("rx",      formatBytes(net.getBytesRecv()));
            m.put("packets", String.valueOf(packets));
            m.put("errors",  String.valueOf(errors));
            m.put("status",  "up");
            result.add(m);
        }

        return result;
    }

    // ── 포맷 헬퍼 ─────────────────────────────────────────────────

    private static String formatBytes(long bytes) {
        if (bytes < 1_024)             return bytes + " B";
        if (bytes < 1_048_576)         return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824)     return String.format("%.1f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }

    private static String formatBits(long bitsPerSec) {
        if (bitsPerSec >= 1_000_000_000) return (bitsPerSec / 1_000_000_000) + " Gbps";
        if (bitsPerSec >= 1_000_000)     return (bitsPerSec / 1_000_000) + " Mbps";
        return (bitsPerSec / 1_000) + " Kbps";
    }
}
