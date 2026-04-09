package com.example.beacon.config;

/**
 * 방화벽 동기화 채널 조합(서버 측).
 * <ul>
 *   <li>{@link #DUAL} — 리비전·A 스냅샷 + B 푸시 큐(기본)</li>
 *   <li>{@link #PULL_ONLY} — A 스냅샷만; B 큐 INSERT 생략(에이전트 PowerShell 중복 완화)</li>
 * </ul>
 */
public enum FirewallChannelMode {

    DUAL,
    PULL_ONLY
}
