package com.example.beacon.controller;

import com.example.beacon.service.SecurityEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final SecurityEventService securityEventService;

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        try {
            long count = securityEventService.countUnresolvedEvents();
            model.addAttribute("navBadgeCount", count);
        } catch (Exception e) {
            model.addAttribute("navBadgeCount", 0L);
        }
    }
}
