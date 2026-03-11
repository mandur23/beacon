package com.example.beacon.service;

import com.example.beacon.entity.Agent;
import com.example.beacon.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    @Transactional
    public void incrementEventCount(String agentName) {
        agentRepository.findByAgentName(agentName).ifPresent(agent -> {
            agent.setTotalEvents(agent.getTotalEvents() + 1);
            agentRepository.save(agent);
        });
    }
    
    @Transactional
    public void incrementEventCountByIp(String ipAddress) {
        agentRepository.findByIpAddress(ipAddress).ifPresent(agent -> {
            agent.setTotalEvents(agent.getTotalEvents() + 1);
            agentRepository.save(agent);
        });
    }
    
    @Transactional
    public void incrementTrafficCount(String agentName) {
        agentRepository.findByAgentName(agentName).ifPresent(agent -> {
            agent.setTotalTrafficLogs(agent.getTotalTrafficLogs() + 1);
            agentRepository.save(agent);
        });
    }
    
    @Transactional
    public void incrementTrafficCountByIp(String ipAddress) {
        agentRepository.findByIpAddress(ipAddress).ifPresent(agent -> {
            agent.setTotalTrafficLogs(agent.getTotalTrafficLogs() + 1);
            agentRepository.save(agent);
        });
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
    
    @Transactional
    public void updateAgentStatus() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);
        
        List<Agent> onlineAgents = agentRepository.findOnlineAgents(threshold);
        onlineAgents.forEach(agent -> {
            if (!"online".equals(agent.getStatus())) {
                agent.setStatus("online");
                agentRepository.save(agent);
            }
        });
        
        List<Agent> offlineAgents = agentRepository.findOfflineAgents(threshold);
        offlineAgents.forEach(agent -> {
            if (!"offline".equals(agent.getStatus())) {
                agent.setStatus("offline");
                agentRepository.save(agent);
                log.warn("Agent went offline: {}", agent.getAgentName());
            }
        });
    }
    
    @Transactional
    public void deleteAgent(Long agentId) {
        agentRepository.deleteById(agentId);
    }
}
