package com.example.beacon.entity;

/**
 * SecurityEvent의 수집 경로를 나타낸다.
 * 이를 통해 대시보드·위협 지표에서 출처별 필터링과 신뢰도 구분이 가능하다.
 */
public enum EventSource {
    /** Suricata EVE JSON 파일(eve.json tail 방식) */
    SURICATA,
    /** UDP Syslog로 전달된 Suricata 이벤트 */
    SYSLOG,
    /** API를 통해 에이전트가 전송한 이벤트 */
    AGENT,
    /** 관리자가 위협 화면에서 수동으로 처리한 이벤트 */
    MANUAL
}
