package com.example.beacon.controller;

import com.example.beacon.dto.firewall.FirewallCommandsPollResponse;
import com.example.beacon.dto.firewall.FirewallDesiredStateResponse;
import com.example.beacon.dto.firewall.FirewallStatusReportRequest;
import com.example.beacon.entity.Agent;
import com.example.beacon.security.JwtUserIdExtractor;
import com.example.beacon.service.AgentResolutionService;
import com.example.beacon.service.AgentService;
import com.example.beacon.service.FirewallDesiredStateService;
import com.example.beacon.service.FirewallLongPollService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/agents/me")
@RequiredArgsConstructor
public class AgentMeFirewallController {

    private final JwtUserIdExtractor jwtUserIdExtractor;
    private final AgentResolutionService agentResolutionService;
    private final FirewallDesiredStateService firewallDesiredStateService;
    private final FirewallLongPollService firewallLongPollService;
    private final AgentService agentService;
    @Qualifier("firewallLongPollExecutor")
    private final TaskExecutor firewallLongPollExecutor;

    @GetMapping("/firewall-desired-state")
    public ResponseEntity<FirewallDesiredStateResponse> getDesiredState(
            HttpServletRequest request,
            @RequestParam(required = false) String agentName) {

        resolveAgent(request, agentName);
        return ResponseEntity.ok(firewallDesiredStateService.buildDesiredState());
    }

    /**
     * 채널 B: since 이후 명령을 최대 약 55초까지 대기하며 반환한다(롱폴).
     * <p>{@code since}·응답 {@code lastCommandId}는 항상 {@code firewall_agent_commands.id}(숫자 PK)이다.
     * {@code commandId}(UUID)를 since에 넣으면 안 된다.</p>
     */
    @GetMapping("/firewall-commands")
    public DeferredResult<ResponseEntity<FirewallCommandsPollResponse>> pollCommands(
            HttpServletRequest request,
            @RequestParam(required = false) String agentName,
            @RequestParam(name = "since", defaultValue = "0") long sinceCommandId) {

        final Agent agent;
        try {
            agent = resolveAgent(request, agentName);
        } catch (RuntimeException e) {
            DeferredResult<ResponseEntity<FirewallCommandsPollResponse>> fail = new DeferredResult<>();
            fail.setErrorResult(e);
            return fail;
        }

        DeferredResult<ResponseEntity<FirewallCommandsPollResponse>> result = new DeferredResult<>(56_000L);
        firewallLongPollExecutor.execute(() -> {
            try {
                FirewallCommandsPollResponse body = firewallLongPollService.longPoll(agent.getId(), sinceCommandId);
                result.setResult(ResponseEntity.ok(body));
            } catch (Exception ex) {
                result.setErrorResult(ex);
            }
        });
        return result;
    }

    @PostMapping("/firewall-status")
    public ResponseEntity<Map<String, Object>> reportStatus(
            HttpServletRequest request,
            @RequestParam(required = false) String agentName,
            @Valid @RequestBody FirewallStatusReportRequest body) {

        Agent agent = resolveAgent(request, agentName);
        agentService.recordFirewallStatus(agent, body);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "상태가 기록되었습니다."
        ));
    }

    private Agent resolveAgent(HttpServletRequest request, String agentName) {
        Long userId = requireUserId(request);
        return agentResolutionService.resolveAgentForUser(userId, agentName, isAdmin());
    }

    private Long requireUserId(HttpServletRequest request) {
        Long userId = jwtUserIdExtractor.extractUserId(request);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization Bearer JWT가 필요합니다.");
        }
        return userId;
    }

    private boolean isAdmin() {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
