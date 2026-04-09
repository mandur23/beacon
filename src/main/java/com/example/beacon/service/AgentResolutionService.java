package com.example.beacon.service;

import com.example.beacon.entity.Agent;
import com.example.beacon.exception.ResourceNotFoundException;
import com.example.beacon.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentResolutionService {

    private final AgentRepository agentRepository;

    /**
     * JWT 사용자 기준 에이전트를 결정한다.
     * <ul>
     *   <li>ADMIN + agentName: 해당 이름의 에이전트(운영용)</li>
     *   <li>agentName: 소유자(owner_user_id)가 일치해야 함</li>
     *   <li>agentName 생략: 소유 에이전트가 정확히 1대일 때만 허용</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Agent resolveAgentForUser(long userId, String agentName, boolean admin) {
        if (admin && agentName != null && !agentName.isBlank()) {
            return agentRepository.findByAgentName(agentName.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent", agentName.trim()));
        }

        if (agentName != null && !agentName.isBlank()) {
            Agent a = agentRepository.findByAgentName(agentName.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent", agentName.trim()));
            if (a.getOwnerUserId() == null || !a.getOwnerUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 에이전트에 대한 접근 권한이 없습니다.");
            }
            return a;
        }

        List<Agent> owned = agentRepository.findByOwnerUserId(userId);
        if (owned.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "소유한 에이전트가 없습니다. 관리자에게 owner_user 연결을 요청하세요.");
        }
        if (owned.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "여러 에이전트가 연결되어 있습니다. agentName 파라미터를 지정하세요.");
        }
        return owned.get(0);
    }
}
