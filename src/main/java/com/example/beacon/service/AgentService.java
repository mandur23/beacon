package com.example.beacon.service;

import com.example.beacon.entity.Agent;
import com.example.beacon.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {
    
    private final AgentRepository agentRepository;
    
    private static final int HEARTBEAT_TIMEOUT_MINUTES = 5;
    
    @Transactional
    public Agent registerOrUpdateAgent(String agentName, String hostname, String ipAddress,
                                      String osType, String osVersion, String agentVersion,
                                      String username, String metadata) {
        Optional<Agent> existing = agentRepository.findByAgentName(agentName);
        
        if (existing.isPresent()) {
            Agent agent = existing.get();
            agent.setHostname(hostname);
            agent.setIpAddress(ipAddress);
            agent.setOsType(osType);
            agent.setOsVersion(osVersion);
            agent.setAgentVersion(agentVersion);
            agent.setUsername(username);
            agent.setMetadata(metadata);
            agent.setStatus("online");
            agent.setLastHeartbeat(LocalDateTime.now());
            log.info("Agent updated: {}", agentName);
            return agentRepository.save(agent);
        } else {
            Agent newAgent = Agent.builder()
                    .agentName(agentName)
                    .hostname(hostname)
                    .ipAddress(ipAddress)
                    .osType(osType)
                    .osVersion(osVersion)
                    .agentVersion(agentVersion)
                    .username(username)
                    .metadata(metadata)
                    .status("online")
                    .lastHeartbeat(LocalDateTime.now())
                    .totalEvents(0L)
                    .totalTrafficLogs(0L)
                    .build();
            log.info("New agent registered: {}", agentName);
            return agentRepository.save(newAgent);
        }
    }
    
    @Transactional
    public void updateHeartbeat(String agentName) {
        agentRepository.findByAgentName(agentName).ifPresent(agent -> {
            agent.setLastHeartbeat(LocalDateTime.now());
            agent.setStatus("online");
            agentRepository.save(agent);
        });
    }

    /**
     * 단일 UPDATE 쿼리로 에이전트 이벤트 카운터를 증가시킨다.
     * 이름에 해당하는 에이전트가 없으면 조용히 무시한다.
     */
    @Transactional
    public void incrementEventCount(String agentName) {
        agentRepository.incrementEventCountByName(agentName);
    }

    /**
     * 단일 UPDATE 쿼리로 IP 기반 에이전트 이벤트 카운터를 증가시킨다.
     */
    @Transactional
    public void incrementEventCountByIp(String ipAddress) {
        agentRepository.incrementEventCountByIp(ipAddress);
    }

    /**
     * 단일 UPDATE 쿼리로 에이전트 트래픽 카운터를 증가시킨다.
     */
    @Transactional
    public void incrementTrafficCount(String agentName) {
        agentRepository.incrementTrafficCountByName(agentName);
    }

    /**
     * 단일 UPDATE 쿼리로 IP 기반 에이전트 트래픽 카운터를 증가시킨다.
     */
    @Transactional
    public void incrementTrafficCountByIp(String ipAddress) {
        agentRepository.incrementTrafficCountByIp(ipAddress);
    }
    
    @Transactional(readOnly = true)
    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public List<Agent> getOnlineAgents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);
        return agentRepository.findOnlineAgents(threshold);
    }
    
    @Transactional(readOnly = true)
    public List<Agent> getOfflineAgents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);
        return agentRepository.findOfflineAgents(threshold);
    }
    
    @Transactional(readOnly = true)
    public Long getOnlineAgentCount() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);
        return agentRepository.countActiveAgents(threshold);
    }

    /**
     * 타임아웃된 에이전트를 단일 벌크 UPDATE 쿼리로 offline 처리한다.
     * 스케줄러에 의해 60초마다 자동 실행된다.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void updateAgentStatus() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);
        int updated = agentRepository.bulkMarkOffline(threshold);
        if (updated > 0) {
            log.warn("Offline 처리된 에이전트 수: {}", updated);
        }
    }

    @Transactional
    public void disconnectAgent(String agentName) {
        agentRepository.findByAgentName(agentName).ifPresent(agent -> {
            agent.setStatus("offline");
            agentRepository.save(agent);
            log.info("Agent explicitly disconnected: {}", agentName);
        });
    }
    
    @Transactional
    public void deleteAgent(Long agentId) {
        agentRepository.deleteById(agentId);
    }
}
