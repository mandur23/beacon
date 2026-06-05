package com.example.beacon.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "guardian.correlation")
@Getter
@Setter
public class CorrelationPolicyProperties {

    private List<String> adminSourceIps = new ArrayList<>(List.of(
            "192.168.1.30",
            "192.168.1.10"
    ));

    private List<String> adminDestinationIps = new ArrayList<>(List.of(
            "192.168.1.10"
    ));

    private List<String> ignoredEventTypes = new ArrayList<>(List.of(
            "biometric_anomaly"
    ));

    private List<String> ignoredAgentNames = new ArrayList<>(List.of(
            "suricata-local"
    ));

    private List<String> localLoopbackIps = new ArrayList<>(List.of(
            "127.0.1.1",
            "127.0.0.1"
    ));

    private List<String> rpiGuardianSourceIps = new ArrayList<>(List.of(
            "192.168.1.10",
            "127.0.1.1",
            "127.0.0.1"
    ));

    private List<String> ignoredRpiGuardianDestinationIps = new ArrayList<>(List.of(
            "192.168.1.30"
    ));

    private List<String> ignoredDescriptionKeywords = new ArrayList<>(List.of(
            "ids_http",
            "ids_fileinfo",
            "python basehttp serverbanner",
            "/api/health",
            "/api/model/status",
            "/static/",
            "misc activity"
    ));

    private List<String> suspiciousExecutionHints = new ArrayList<>(List.of(
            "powershell",
            "cmd.exe",
            "wscript",
            "mshta",
            "encodedcommand",
            "curl.exe",
            "wget",
            "bash -c",
            "sh -c",
            "python -c"
    ));

    private List<String> securityToolingHints = new ArrayList<>(List.of(
            "python",
            "agent",
            "wazuh",
            "suricata",
            "beacon",
            "defender",
            "security"
    ));

    private List<String> executionIndicators = new ArrayList<>(List.of(
            "powershell",
            "cmd",
            "wscript",
            "mshta",
            "encodedcommand"
    ));

    private List<String> networkIndicators = new ArrayList<>(List.of(
            "external ip lookup",
            "windows powershell user-agent",
            "dns lookup",
            "c2"
    ));

    private List<String> persistenceIndicators = new ArrayList<>(List.of(
            "startup",
            "schtasks",
            "service create",
            "run key",
            "persistence",
            "currentversion\\run",
            "\\runonce",
            "scheduled task",
            "startupapproved"
    ));

    private List<String> defenseEvasionIndicators = new ArrayList<>(List.of(
            "agent_offline",
            "heartbeat lost",
            "beacon stop",
            "disable"
    ));

    private List<String> impactIndicators = new ArrayList<>(List.of(
            "encrypt",
            "ransom",
            "shadow copy",
            "delete shadows",
            "vssadmin",
            "mass file",
            "mass rename",
            "bulk delete"
    ));

    private List<String> suspiciousFileExtensions = new ArrayList<>(List.of(
            ".ps1",
            ".bat",
            ".cmd",
            ".vbs",
            ".js",
            ".exe",
            ".dll",
            ".zip",
            ".rar",
            ".7z",
            ".iso"
    ));

    private List<String> suspiciousFileLocations = new ArrayList<>(List.of(
            "\\desktop\\",
            "\\downloads\\",
            "\\documents\\",
            "\\public\\",
            "\\temp\\"
    ));

    private List<String> startupPathIndicators = new ArrayList<>(List.of(
            "\\start menu\\programs\\startup\\",
            "\\startup\\",
            "\\appdata\\roaming\\microsoft\\windows\\start menu\\programs\\startup\\"
    ));

    private WazuhPolicy wazuh = new WazuhPolicy();

    @PostConstruct
    void ensureDefaults() {
        adminSourceIps = nullSafeList(adminSourceIps);
        adminDestinationIps = nullSafeList(adminDestinationIps);
        ignoredEventTypes = nullSafeList(ignoredEventTypes);
        ignoredAgentNames = nullSafeList(ignoredAgentNames);
        localLoopbackIps = nullSafeList(localLoopbackIps);
        rpiGuardianSourceIps = nullSafeList(rpiGuardianSourceIps);
        ignoredRpiGuardianDestinationIps = nullSafeList(ignoredRpiGuardianDestinationIps);
        ignoredDescriptionKeywords = nullSafeList(ignoredDescriptionKeywords);
        suspiciousExecutionHints = nullSafeList(suspiciousExecutionHints);
        securityToolingHints = nullSafeList(securityToolingHints);
        executionIndicators = nullSafeList(executionIndicators);
        networkIndicators = nullSafeList(networkIndicators);
        persistenceIndicators = nullSafeList(persistenceIndicators);
        defenseEvasionIndicators = nullSafeList(defenseEvasionIndicators);
        impactIndicators = nullSafeList(impactIndicators);
        suspiciousFileExtensions = nullSafeList(suspiciousFileExtensions);
        suspiciousFileLocations = nullSafeList(suspiciousFileLocations);
        startupPathIndicators = nullSafeList(startupPathIndicators);
        if (wazuh == null) {
            wazuh = new WazuhPolicy();
        }
    }

    private static List<String> nullSafeList(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }

    @Getter
    @Setter
    public static class WazuhPolicy {
        private int preserveLevelMin = 10;
        private int criticalLevelMin = 12;
        private int highLevelMin = 10;
        private int mediumLevelMin = 5;
        private double lowRiskFloor = 15.0;
        private double mediumRiskFloor = 50.0;
        private double highRiskFloor = 80.0;
        private double criticalRiskFloor = 95.0;
    }
}
