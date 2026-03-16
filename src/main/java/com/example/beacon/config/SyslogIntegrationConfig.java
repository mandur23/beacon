package com.example.beacon.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.ip.udp.UnicastReceivingChannelAdapter;
import org.springframework.messaging.MessageChannel;

@Configuration
public class SyslogIntegrationConfig {

    @Value("${syslog.port:514}")
    private int syslogPort;

    @Bean
    public MessageChannel syslogChannel() {
        return new DirectChannel();
    }

    @Bean
    public UnicastReceivingChannelAdapter syslogAdapter(MessageChannel syslogChannel) {
        UnicastReceivingChannelAdapter adapter = new UnicastReceivingChannelAdapter(syslogPort);
        adapter.setOutputChannel(syslogChannel);
        adapter.setAutoStartup(true);
        return adapter;
    }
}
