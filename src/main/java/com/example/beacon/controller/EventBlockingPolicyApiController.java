package com.example.beacon.controller;

import com.example.beacon.dto.EventBlockingPolicyRequest;
import com.example.beacon.entity.EventBlockingPolicy;
import com.example.beacon.service.EventBlockingPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/event-blocking-policies")
@RequiredArgsConstructor
public class EventBlockingPolicyApiController {

    private final EventBlockingPolicyService eventBlockingPolicyService;

    @GetMapping
    public ResponseEntity<List<EventBlockingPolicy>> list() {
        return ResponseEntity.ok(eventBlockingPolicyService.findAllOrdered());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody EventBlockingPolicyRequest req) {
        EventBlockingPolicy saved = eventBlockingPolicyService.create(req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "정책이 추가되었습니다.",
                "id", saved.getId()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @Valid @RequestBody EventBlockingPolicyRequest req) {
        eventBlockingPolicyService.update(id, req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "정책이 수정되었습니다."
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        eventBlockingPolicyService.delete(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "정책이 삭제되었습니다."
        ));
    }
}
