package com.example.beacon.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * 방화벽 B채널(명령 큐)·롱폴 관련 설정.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "beacon.firewall")
public class BeaconFirewallProperties {

    /**
     * firewall_agent_commands 행 보관 일수. 매일 스케줄로 그보다 오래된 행을 삭제한다.
     */
    @Min(1)
    @Max(3650)
    private int commandRetentionDays = 14;

    /**
     * {@link FirewallChannelMode#PULL_ONLY} 이면 규칙 변경 시 B(푸시) 큐에 넣지 않는다.
     * 리비전·GET desired-state는 그대로이다.
     */
    private FirewallChannelMode channelMode = FirewallChannelMode.DUAL;

    @NestedConfigurationProperty
    private final LongPoll longPoll = new LongPoll();

    @Data
    public static class LongPoll {
        private int corePoolSize = 16;
        private int maxPoolSize = 64;
        private int queueCapacity = 500;
    }
}
