package com.example.beacon.controller;

import com.example.beacon.entity.User;
import com.example.beacon.repository.UserRepository;
import com.example.beacon.security.MfaEnforcementFilter;
import com.example.beacon.service.TotpService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class MfaController {

    private final UserRepository userRepository;
    private final TotpService totpService;

    @GetMapping("/mfa-challenge")
    public String mfaChallenge(Authentication authentication, HttpSession session) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        boolean verified = Boolean.TRUE.equals(session.getAttribute(MfaEnforcementFilter.MFA_VERIFIED_SESSION_KEY));
        if (verified) {
            return "redirect:/dashboard";
        }
        return "mfa-challenge";
    }

    @PostMapping("/mfa/verify")
    public String verifyMfa(@RequestParam("otpCode") String otpCode,
                            Authentication authentication,
                            HttpSession session) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaSecret() == null) {
            return "redirect:/login?error";
        }

        if (!totpService.verifyTotp(user.getMfaSecret(), otpCode)) {
            return "redirect:/mfa-challenge?error";
        }

        session.setAttribute(MfaEnforcementFilter.MFA_VERIFIED_SESSION_KEY, true);
        return "redirect:/dashboard";
    }
}
