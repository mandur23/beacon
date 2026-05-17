package com.example.beacon.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP -> 위경도/도시 변환 서비스.
 *
 * - 외부 GeoIP API(ip-api.com)는 호출 비용이 있고 속도 제한(45 req/min)이 있으므로
 *   인메모리 캐시로 IP 1개당 한 번만 조회한다.
 * - 사설/링크-로컬/루프백 IP는 외부 호출 없이 "내부 거점" 좌표로 매핑한다.
 *   (서울 좌표를 기본으로 사용; 추후 서버 위치 설정으로 분리 가능)
 * - 조회 실패 IP는 일정 시간 동안 negative 캐시에 남겨 외부 호출 폭주를 막는다.
 */
@Slf4j
@Service
public class GeoIpService {

    public record GeoLocation(double lat,
                              double lon,
                              String city,
                              String country,
                              String code,   // 3글자 도시 약어 (BEACON Globe label용)
                              boolean isLocal) {}

    private static final long POSITIVE_TTL_MILLIS = Duration.ofHours(24).toMillis();
    private static final long NEGATIVE_TTL_MILLIS = Duration.ofMinutes(10).toMillis();

    private static final GeoLocation INTERNAL_FALLBACK =
            new GeoLocation(37.55, 126.97, "Seoul", "Korea", "SEL", true);

    private final RestTemplate restTemplate = new RestTemplate();

    private record CacheEntry(GeoLocation loc, Instant expiresAt) {}

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 캐시 우선 조회. 실패 시 외부 API 호출.
     * 외부 사용자에게 노출되는 IP만 호출하고, 사설망/루프백은 내부 거점으로 매핑한다.
     */
    public GeoLocation lookup(String ip) {
        if (ip == null || ip.isBlank()) return INTERNAL_FALLBACK;

        String key = ip.trim();
        CacheEntry e = cache.get(key);
        if (e != null && e.expiresAt.isAfter(Instant.now())) {
            return e.loc;
        }

        if (isLocalAddress(key)) {
            put(key, INTERNAL_FALLBACK, POSITIVE_TTL_MILLIS);
            return INTERNAL_FALLBACK;
        }

        GeoLocation fetched = fetchFromIpApi(key);
        if (fetched != null) {
            put(key, fetched, POSITIVE_TTL_MILLIS);
            return fetched;
        }

        // 실패 시 negative 캐시에 internal fallback을 짧게 저장
        put(key, INTERNAL_FALLBACK, NEGATIVE_TTL_MILLIS);
        return INTERNAL_FALLBACK;
    }

    private void put(String key, GeoLocation loc, long ttlMillis) {
        cache.put(key, new CacheEntry(loc, Instant.now().plusMillis(ttlMillis)));
    }

    /**
     * ip-api.com 무료 엔드포인트 (HTTP만). 응답 예:
     * { "status":"success","country":"United States","city":"New York","lat":40.7,"lon":-74.0, ... }
     */
    @SuppressWarnings("unchecked")
    private GeoLocation fetchFromIpApi(String ip) {
        try {
            String url = "http://ip-api.com/json/" + ip
                    + "?fields=status,country,countryCode,city,lat,lon";
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp == null || !"success".equals(resp.get("status"))) return null;

            Object lat = resp.get("lat");
            Object lon = resp.get("lon");
            if (!(lat instanceof Number) || !(lon instanceof Number)) return null;

            String city = Objects.toString(resp.get("city"), "Unknown");
            String country = Objects.toString(resp.get("country"), "");
            String code = makeCityCode(city, Objects.toString(resp.get("countryCode"), ""));

            return new GeoLocation(
                    ((Number) lat).doubleValue(),
                    ((Number) lon).doubleValue(),
                    city,
                    country,
                    code,
                    false);
        } catch (Exception ex) {
            log.debug("[GeoIP] {} lookup 실패: {}", ip, ex.getMessage());
            return null;
        }
    }

    /** 도시명 앞 3글자(영문)나 국가 코드를 fallback으로 사용. */
    private String makeCityCode(String city, String countryCode) {
        if (city != null && !city.isBlank()) {
            String trimmed = city.replaceAll("[^A-Za-z]", "");
            if (trimmed.length() >= 3) {
                return trimmed.substring(0, 3).toUpperCase(Locale.ROOT);
            }
        }
        if (countryCode != null && !countryCode.isBlank()) {
            return countryCode.toUpperCase(Locale.ROOT);
        }
        return "UNK";
    }

    /**
     * RFC1918 사설 / 링크-로컬 / 루프백 / 멀티캐스트 / IPv6 사설 범위 판정.
     * (정확도보다 호출 폭주 방지가 목적이므로 보수적으로 매칭)
     */
    public boolean isLocalAddress(String ip) {
        if (ip == null) return true;
        if (ip.startsWith("10.")
                || ip.startsWith("127.")
                || ip.startsWith("169.254.")
                || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            int second = secondOctet(ip);
            if (second >= 16 && second <= 31) return true;
        }
        // 단순한 IPv6 사설/루프백 판정
        if (ip.equals("::1") || ip.startsWith("fe80:") || ip.startsWith("fc")
                || ip.startsWith("fd")) {
            return true;
        }
        return false;
    }

    private int secondOctet(String ip) {
        int dot1 = ip.indexOf('.');
        int dot2 = dot1 < 0 ? -1 : ip.indexOf('.', dot1 + 1);
        if (dot1 < 0 || dot2 < 0) return -1;
        try {
            return Integer.parseInt(ip.substring(dot1 + 1, dot2));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
