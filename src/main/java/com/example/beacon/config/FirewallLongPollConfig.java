package com.example.beacon.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 롱폴 전용 스레드 풀. 동시에 많은 에이전트가 붙을수록 max·queue를 키우는 것이 안전하다.
 * 트래픽이 매우 크면 WebFlux/비동기 MVC 등으로 블로킹 스레드 수를 줄이는 방안을 검토한다.
 */
@Configuration
@RequiredArgsConstructor
public class FirewallLongPollConfig {

    private final BeaconFirewallProperties beaconFirewallProperties;

    @Bean(name = "firewallLongPollExecutor")
    public TaskExecutor firewallLongPollExecutor() {
        BeaconFirewallProperties.LongPoll lp = beaconFirewallProperties.getLongPoll();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(lp.getCorePoolSize());
        ex.setMaxPoolSize(lp.getMaxPoolSize());
        ex.setQueueCapacity(lp.getQueueCapacity());
        ex.setThreadNamePrefix("fwlp-");
        ex.initialize();
        return ex;
    }
}
