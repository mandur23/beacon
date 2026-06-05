package com.example.beacon.service;

import com.example.beacon.config.CorrelationPolicyProperties;
import com.example.beacon.entity.SecurityEvent;
import com.example.beacon.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventCorrelationService {

    private static final Duration CORRELATION_WINDOW = Duration.ofMinutes(10);
    private static final DateTimeFormatter INCIDENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final SecurityEventRepository securityEventRepository;
    private final CorrelationPolicyProperties correlationPolicyProperties;

    @Transactional
    public SecurityEvent correlate(SecurityEvent savedEvent) {
        if (savedEvent == null) {
            throw new IllegalArgumentException("savedEvent must not be null");
        }
        if (savedEvent.getId() == null) {
            savedEvent.setCorrelationStatus("SINGLE_EVENT");
            return securityEventRepository.save(savedEvent);
        }

        LocalDateTime pivot = savedEvent.getCreatedAt() != null ? savedEvent.getCreatedAt() : LocalDateTime.now();
        LocalDateTime since = pivot.minus(CORRELATION_WINDOW);
        String principalIp = resolvePrincipalIp(savedEvent);
        String principalAgentName = normalize(savedEvent.getAgentName());

        if (shouldIgnoreNoise(savedEvent)) {
            savedEvent.setPrincipalIp(principalIp);
            savedEvent.setCorrelationStatus("IGNORED_NOISE");
            savedEvent.setIncidentKey(null);
            savedEvent.setIncidentType(null);
            applyNoiseNormalization(savedEvent);
            return securityEventRepository.save(savedEvent);
        }

        if (!isCorrelationRelevant(savedEvent)) {
            savedEvent.setPrincipalIp(principalIp);
            savedEvent.setCorrelationStatus("SINGLE_EVENT");
            savedEvent.setIncidentKey(null);
            savedEvent.setIncidentType(null);
            applySingleEventPriorityFloor(savedEvent);
            return securityEventRepository.save(savedEvent);
        }

        List<SecurityEvent> candidates = securityEventRepository.findRecentCorrelationCandidates(
                principalAgentName,
                principalIp,
                since
        );
        candidates.removeIf(this::shouldIgnoreNoise);

        if (candidates.stream().noneMatch(event -> savedEvent.getId().equals(event.getId()))) {
            candidates.add(savedEvent);
        }

        if (isLowSignalExternalLookup(savedEvent) || isLookupSupportingEvent(savedEvent)) {
            candidates.removeIf(event -> !isLowSignalExternalLookup(event) && !isLookupSupportingEvent(event));
        }

        candidates.removeIf(event -> !savedEvent.getId().equals(event.getId()) && !isCorrelationRelevant(event));

        String incidentType = determineIncidentType(candidates);
        if (incidentType == null) {
            savedEvent.setPrincipalIp(principalIp);
            savedEvent.setCorrelationStatus("SINGLE_EVENT");
            applySingleEventPriorityFloor(savedEvent);
            return securityEventRepository.save(savedEvent);
        }

        String incidentKey = buildIncidentKey(savedEvent, principalAgentName, principalIp, pivot);

        String correlationStatus = determineCorrelationStatus(candidates, incidentType);
        boolean lowSignalOutboundOnly = isLowSignalOutboundOnly(candidates, incidentType);
        double boostedRiskScore = Math.max(savedEvent.getRiskScore() != null ? savedEvent.getRiskScore() : 0.0,
                calculateBoostedRiskScore(candidates, incidentType));
        String minimumSeverity = determineMinimumSeverity(incidentType, lowSignalOutboundOnly);

        for (SecurityEvent event : candidates) {
            event.setPrincipalIp(resolvePrincipalIp(event));
            event.setIncidentKey(incidentKey);
            event.setIncidentType(incidentType);
            event.setCorrelationStatus(correlationStatus);
            if (lowSignalOutboundOnly) {
                event.setRiskScore(35.0);
                event.setSeverity(maxSeverity(event.getSeverity(), minimumSeverity));
                continue;
            }
            if (event.getRiskScore() == null || event.getRiskScore() < boostedRiskScore) {
                event.setRiskScore(boostedRiskScore);
            }
            event.setSeverity(maxSeverity(event.getSeverity(), minimumSeverity));
        }

        securityEventRepository.saveAll(candidates);
        return candidates.stream()
                .filter(event -> savedEvent.getId().equals(event.getId()))
                .findFirst()
                .orElse(savedEvent);
    }

    private String determineIncidentType(List<SecurityEvent> events) {
        Set<String> categories = new LinkedHashSet<>();
        long agentDisconnectCount = 0;
        for (SecurityEvent event : events) {
            Set<String> cat = categorize(event);
            categories.addAll(cat);
            if (cat.contains("AGENT_DISCONNECT")) {
                agentDisconnectCount++;
            }
        }

        boolean hasAgentDisconnect = categories.contains("AGENT_DISCONNECT");
        categories.remove("AGENT_DISCONNECT");

        // Advanced Agent Offline Logic
        if (hasAgentDisconnect) {
            if (categories.contains("EXECUTION") && categories.contains("NETWORK")) {
                return "DEFENSE_EVASION_SUSPECTED";
            }
            if (categories.contains("DEFENSE_EVASION")) {
                return "DEFENSE_EVASION_SUSPECTED";
            }
            if (agentDisconnectCount >= 3) {
                return "DEFENSE_EVASION_SUSPECTED";
            }
        }

        if (categories.contains("FILE") && categories.contains("DEFENSE_EVASION")) {
            return "DESTRUCTIVE_ACTIVITY_SUSPECTED";
        }
        if (categories.contains("IMPACT") && (categories.contains("EXECUTION") || categories.contains("NETWORK"))) {
            return "DESTRUCTIVE_ACTIVITY_SUSPECTED";
        }
        if (categories.contains("PERSISTENCE") && (categories.contains("EXECUTION") || categories.contains("NETWORK") || categories.contains("FILE"))) {
            return "MULTI_STAGE_COMPROMISE";
        }
        if (categories.contains("FILE") && (categories.contains("EXECUTION") || categories.contains("NETWORK"))) {
            return "MULTI_STAGE_COMPROMISE";
        }
        if (categories.size() >= 3) {
            return "MULTI_STAGE_COMPROMISE";
        }
        if (categories.contains("DEFENSE_EVASION")) {
            return "DEFENSE_EVASION_SUSPECTED";
        }
        if (categories.contains("PERSISTENCE")) {
            return "PERSISTENCE_SUSPECTED";
        }
        if (categories.contains("FILE")) {
            return "SUSPICIOUS_FILE_DELIVERY";
        }
        if (categories.contains("NETWORK")) {
            return "SUSPICIOUS_OUTBOUND";
        }
        if (categories.contains("EXECUTION")) {
            return "SUSPICIOUS_EXECUTION";
        }
        return null;
    }

    private Set<String> categorize(SecurityEvent event) {
        Set<String> categories = new LinkedHashSet<>();
        String eventType = normalize(event.getEventType());
        String description = normalize(event.getDescription());

        if (containsConfiguredKeyword(eventType, description, correlationPolicyProperties.getExecutionIndicators())
                || (containsAny(eventType, description, "process_start", "process_creation")
                && containsSuspiciousExecutionHint(description))) {
            categories.add("EXECUTION");
        }
        if (containsAny(eventType, description, "agent_offline", "heartbeat lost")) {
            categories.add("AGENT_DISCONNECT");
        }
        if (containsConfiguredKeyword(eventType, description, correlationPolicyProperties.getNetworkIndicators())) {
            categories.add("NETWORK");
        }
        if (containsConfiguredKeyword(eventType, description, correlationPolicyProperties.getDefenseEvasionIndicators())
                || ((containsAny(eventType, description, "process_stop", "service stop"))
                && containsSecurityToolingHint(description))) {
            categories.add("DEFENSE_EVASION");
        }
        if (containsConfiguredKeyword(eventType, description, correlationPolicyProperties.getPersistenceIndicators())) {
            categories.add("PERSISTENCE");
        }
        if (containsAny(eventType, description,
                "file_created", "file_modified", "directory modified")
                && containsConfiguredKeyword("", description, correlationPolicyProperties.getStartupPathIndicators())) {
            categories.add("PERSISTENCE");
        }
        if (containsAny(eventType, description,
                "file_created", "file_modified")
                && isSuspiciousFileDelivery(description)) {
            categories.add("FILE");
        }
        if (containsConfiguredKeyword(eventType, description, correlationPolicyProperties.getImpactIndicators())) {
            categories.add("IMPACT");
        }

        return categories;
    }

    private boolean containsAny(String eventType, String description, String... needles) {
        for (String needle : needles) {
            if (eventType.contains(needle) || description.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldIgnoreNoise(SecurityEvent event) {
        String agentName = normalize(event.getAgentName());
        String eventType = normalize(event.getEventType());
        String description = normalize(event.getDescription());
        String sourceIp = normalize(event.getSourceIp());
        String destinationIp = normalize(event.getDestinationIp());
        Integer wazuhRuleLevel = extractWazuhRuleLevel(event);

        if (isEnginePriorityEvent(eventType, event.getOriginalSeverity(), wazuhRuleLevel)) {
            return false;
        }

        if (isAdministrativeSshNoise(eventType, sourceIp, destinationIp)) {
            return true;
        }

        if (containsConfiguredKeyword(eventType, description,
                correlationPolicyProperties.getIgnoredDescriptionKeywords())) {
            return true;
        }

        if (correlationPolicyProperties.getIgnoredEventTypes().contains(eventType)) {
            return true;
        }

        if (correlationPolicyProperties.getIgnoredAgentNames().contains(agentName)
                || correlationPolicyProperties.getLocalLoopbackIps().contains(sourceIp)) {
            return true;
        }

        if ("rpi-guardian".equals(agentName)
                && correlationPolicyProperties.getRpiGuardianSourceIps().contains(sourceIp)) {
            if (containsConfiguredKeyword(eventType, description,
                    correlationPolicyProperties.getIgnoredDescriptionKeywords())) {
                return true;
            }
            if (correlationPolicyProperties.getIgnoredRpiGuardianDestinationIps().contains(destinationIp)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSuspiciousFileDelivery(String description) {
        boolean suspiciousExtension = containsConfiguredKeyword("", description,
                correlationPolicyProperties.getSuspiciousFileExtensions());
        boolean riskyLocation = containsConfiguredKeyword("", description,
                correlationPolicyProperties.getSuspiciousFileLocations());
        return suspiciousExtension && riskyLocation;
    }

    private void applyNoiseNormalization(SecurityEvent event) {
        String eventType = normalize(event.getEventType());
        Integer wazuhRuleLevel = extractWazuhRuleLevel(event);

        if (isEnginePriorityEvent(eventType, event.getOriginalSeverity(), wazuhRuleLevel)) {
            String floorSeverity = "wazuh_alert".equals(eventType)
                    ? mapWazuhSeverityFloor(wazuhRuleLevel)
                    : defaultOriginalSeverity(event.getOriginalSeverity());
            double floorRisk = "wazuh_alert".equals(eventType)
                    ? mapWazuhRiskFloor(wazuhRuleLevel)
                    : defaultOriginalRisk(event.getOriginalRiskScore());
            event.setSeverity(maxSeverity(event.getSeverity(), floorSeverity));
            if (event.getRiskScore() == null || event.getRiskScore() < floorRisk) {
                event.setRiskScore(floorRisk);
            }
            return;
        }

        if (eventType.equals("agent_online")) {
            event.setSeverity("INFO");
            event.setRiskScore(0.0);
            return;
        }

        if (eventType.equals("agent_offline")) {
            event.setSeverity("LOW");
            event.setRiskScore(Math.min(event.getRiskScore() != null ? event.getRiskScore() : 15.0, 15.0));
            return;
        }

        if (eventType.equals("ids_ssh")) {
            event.setSeverity("INFO");
            event.setRiskScore(0.0);
            return;
        }

        if (eventType.equals("ssh")) {
            event.setSeverity("LOW");
            event.setRiskScore(Math.min(event.getRiskScore() != null ? event.getRiskScore() : 30.0, 30.0));
            return;
        }

        if (eventType.contains("alert")) {
            event.setSeverity("LOW");
            event.setRiskScore(Math.min(event.getRiskScore() != null ? event.getRiskScore() : 25.0, 25.0));
        }
    }

    private void applySingleEventPriorityFloor(SecurityEvent event) {
        String eventType = normalize(event.getEventType());
        Integer wazuhRuleLevel = extractWazuhRuleLevel(event);
        if (!isEnginePriorityEvent(eventType, event.getOriginalSeverity(), wazuhRuleLevel)) {
            return;
        }

        String floorSeverity = "wazuh_alert".equals(eventType)
                ? mapWazuhSeverityFloor(wazuhRuleLevel)
                : defaultOriginalSeverity(event.getOriginalSeverity());
        double floorRisk = "wazuh_alert".equals(eventType)
                ? mapWazuhRiskFloor(wazuhRuleLevel)
                : defaultOriginalRisk(event.getOriginalRiskScore());

        event.setSeverity(maxSeverity(event.getSeverity(), floorSeverity));
        if (event.getRiskScore() == null || event.getRiskScore() < floorRisk) {
            event.setRiskScore(floorRisk);
        }
    }

    private boolean isCorrelationRelevant(SecurityEvent event) {
        return !categorize(event).isEmpty()
                || isLowSignalExternalLookup(event)
                || isLookupSupportingEvent(event);
    }

    private boolean containsSuspiciousExecutionHint(String description) {
        return containsConfiguredKeyword("", description, correlationPolicyProperties.getSuspiciousExecutionHints());
    }

    private boolean containsSecurityToolingHint(String description) {
        return containsConfiguredKeyword("", description, correlationPolicyProperties.getSecurityToolingHints());
    }

    private boolean isAdministrativeSshNoise(String eventType, String sourceIp, String destinationIp) {
        if (!eventType.equals("ssh") && !eventType.equals("ids_ssh")) {
            return false;
        }

        return correlationPolicyProperties.getAdminDestinationIps().contains(destinationIp)
                || correlationPolicyProperties.getAdminSourceIps().contains(sourceIp);
    }

    private boolean containsConfiguredKeyword(String eventType, String description, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        return keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .anyMatch(keyword -> eventType.contains(keyword) || description.contains(keyword));
    }

    private String determineCorrelationStatus(List<SecurityEvent> events, String incidentType) {
        int uniqueCategoryCount = (int) events.stream()
                .flatMap(event -> categorize(event).stream())
                .distinct()
                .count();

        if ("DESTRUCTIVE_ACTIVITY_SUSPECTED".equals(incidentType) || uniqueCategoryCount >= 3) {
            return "CRITICAL";
        }
        if (events.size() >= 2) {
            return "CORRELATED";
        }
        return "SINGLE_EVENT";
    }

    private double calculateBoostedRiskScore(List<SecurityEvent> events, String incidentType) {
        if (isLowSignalOutboundOnly(events, incidentType)) {
            return 35.0;
        }

        double base = events.stream()
                .map(SecurityEvent::getRiskScore)
                .filter(score -> score != null)
                .max(Comparator.naturalOrder())
                .orElse(0.0);

        double bonus = switch (incidentType) {
            case "SUSPICIOUS_FILE_DELIVERY" -> 15.0;
            case "SUSPICIOUS_EXECUTION" -> 5.0;
            case "SUSPICIOUS_OUTBOUND" -> 10.0;
            case "DEFENSE_EVASION_SUSPECTED" -> 20.0;
            case "PERSISTENCE_SUSPECTED" -> 20.0;
            case "MULTI_STAGE_COMPROMISE" -> 35.0;
            case "DESTRUCTIVE_ACTIVITY_SUSPECTED" -> 40.0;
            default -> 0.0;
        };

        if (events.size() >= 3) {
            bonus += 10.0;
        }

        return Math.min(100.0, base + bonus);
    }

    private String determineMinimumSeverity(String incidentType, boolean lowSignalOutboundOnly) {
        if (lowSignalOutboundOnly) {
            return "LOW";
        }

        return switch (incidentType) {
            case "SUSPICIOUS_FILE_DELIVERY" -> "MEDIUM";
            case "SUSPICIOUS_EXECUTION", "SUSPICIOUS_OUTBOUND" -> "MEDIUM";
            case "DEFENSE_EVASION_SUSPECTED", "PERSISTENCE_SUSPECTED", "MULTI_STAGE_COMPROMISE" -> "HIGH";
            case "DESTRUCTIVE_ACTIVITY_SUSPECTED" -> "CRITICAL";
            default -> "LOW";
        };
    }

    private String maxSeverity(String currentSeverity, String minimumSeverity) {
        if (currentSeverity == null || currentSeverity.isBlank()) {
            return minimumSeverity;
        }
        if (minimumSeverity == null || minimumSeverity.isBlank()) {
            return currentSeverity;
        }
        if (severityRank(currentSeverity) >= severityRank(minimumSeverity)) {
            return currentSeverity;
        }
        return minimumSeverity;
    }

    private int severityRank(String severity) {
        String normalized = normalize(severity);
        return switch (normalized) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private boolean isLowSignalOutboundOnly(List<SecurityEvent> events, String incidentType) {
        if (!"SUSPICIOUS_OUTBOUND".equals(incidentType) || events.isEmpty()) {
            return false;
        }

        boolean hasLowSignalLookup = false;
        for (SecurityEvent event : events) {
            if (isLowSignalExternalLookup(event)) {
                hasLowSignalLookup = true;
                continue;
            }

            if (isLookupSupportingEvent(event)) {
                continue;
            }

            return false;
        }

        return hasLowSignalLookup;
    }

    private boolean isLowSignalExternalLookup(SecurityEvent event) {
        String eventType = normalize(event.getEventType());
        String description = normalize(event.getDescription());
        String metadata = normalize(event.getMetadata());

        if (!eventType.contains("alert")) {
            return false;
        }

        return description.contains("external ip lookup")
                || description.contains("device retrieving external ip address detected")
                || description.contains("ip-api.com")
                || metadata.contains("external ip lookup")
                || metadata.contains("device retrieving external ip address detected")
                || metadata.contains("ip-api.com");
    }

    private boolean isLookupSupportingEvent(SecurityEvent event) {
        String eventType = normalize(event.getEventType());
        String description = normalize(event.getDescription());
        String metadata = normalize(event.getMetadata());
        String agentName = normalize(event.getAgentName());

        boolean lookupMetadata = description.contains("ip-api.com")
                || metadata.contains("ip-api.com")
                || metadata.contains("external ip lookup");

        if (eventType.equals("http") || eventType.equals("ids_http")) {
            return lookupMetadata || description.contains("beaconguardian/1.0") || metadata.contains("beaconguardian/1.0");
        }

        if (eventType.equals("dns") || eventType.equals("ids_dns")) {
            return lookupMetadata;
        }

        if (eventType.equals("ids_fileinfo")) {
            return lookupMetadata || "rpi-guardian".equals(agentName);
        }

        return false;
    }

    private boolean isEnginePriorityEvent(String eventType, String originalSeverity, Integer wazuhRuleLevel) {
        String normalizedOriginalSeverity = normalize(originalSeverity);
        if ("wazuh_alert".equals(eventType)) {
            return (wazuhRuleLevel != null && wazuhRuleLevel >= correlationPolicyProperties.getWazuh().getPreserveLevelMin())
                    || "high".equals(normalizedOriginalSeverity)
                    || "critical".equals(normalizedOriginalSeverity);
        }
        return eventType.contains("alert")
                && ("high".equals(normalizedOriginalSeverity) || "critical".equals(normalizedOriginalSeverity));
    }

    private Integer extractWazuhRuleLevel(SecurityEvent event) {
        String metadata = event.getMetadata();
        if (metadata == null || metadata.isBlank()) {
            return null;
        }

        String marker = "\"level\":";
        int start = metadata.indexOf(marker);
        if (start < 0) {
            return null;
        }

        start += marker.length();
        while (start < metadata.length() && Character.isWhitespace(metadata.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < metadata.length() && Character.isDigit(metadata.charAt(end))) {
            end++;
        }

        if (end == start) {
            return null;
        }

        try {
            return Integer.parseInt(metadata.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String mapWazuhSeverityFloor(Integer wazuhRuleLevel) {
        if (wazuhRuleLevel == null) {
            return "LOW";
        }
        if (wazuhRuleLevel >= correlationPolicyProperties.getWazuh().getCriticalLevelMin()) {
            return "CRITICAL";
        }
        if (wazuhRuleLevel >= correlationPolicyProperties.getWazuh().getHighLevelMin()) {
            return "HIGH";
        }
        if (wazuhRuleLevel >= correlationPolicyProperties.getWazuh().getMediumLevelMin()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double mapWazuhRiskFloor(Integer wazuhRuleLevel) {
        if (wazuhRuleLevel == null) {
            return correlationPolicyProperties.getWazuh().getLowRiskFloor();
        }
        if (wazuhRuleLevel >= correlationPolicyProperties.getWazuh().getCriticalLevelMin()) {
            return correlationPolicyProperties.getWazuh().getCriticalRiskFloor();
        }
        if (wazuhRuleLevel >= correlationPolicyProperties.getWazuh().getHighLevelMin()) {
            return correlationPolicyProperties.getWazuh().getHighRiskFloor();
        }
        if (wazuhRuleLevel >= correlationPolicyProperties.getWazuh().getMediumLevelMin()) {
            return correlationPolicyProperties.getWazuh().getMediumRiskFloor();
        }
        return correlationPolicyProperties.getWazuh().getLowRiskFloor();
    }

    private String defaultOriginalSeverity(String originalSeverity) {
        String normalized = normalize(originalSeverity);
        if (normalized.isBlank()) {
            return "LOW";
        }
        return switch (normalized) {
            case "critical" -> "CRITICAL";
            case "high" -> "HIGH";
            case "medium" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private double defaultOriginalRisk(Double originalRiskScore) {
        if (originalRiskScore == null || originalRiskScore <= 0) {
            return 15.0;
        }
        return Math.max(15.0, Math.min(100.0, originalRiskScore));
    }

    private String buildIncidentKey(SecurityEvent event, String principalAgentName, String principalIp, LocalDateTime pivot) {
        String principal = principalAgentName != null ? principalAgentName : principalIp;
        String normalized = principal == null ? "unknown" : principal.replaceAll("[^A-Za-z0-9_-]", "_");
        return "INC-" + normalized + "-" + pivot.format(INCIDENT_TIME_FORMAT);
    }

    private String resolvePrincipalIp(SecurityEvent event) {
        if (event.getPrincipalIp() != null && !event.getPrincipalIp().isBlank()) {
            return event.getPrincipalIp();
        }
        if (event.getSourceIp() != null && !event.getSourceIp().isBlank()) {
            return event.getSourceIp();
        }
        return "0.0.0.0";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
