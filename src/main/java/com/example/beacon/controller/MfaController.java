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

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
public class MfaController {

    private static final String MFA_OTP_ATTEMPTS_SESSION_KEY = "MFA_OTP_ATTEMPTS";
    private static final String MFA_OTP_LOCKED_UNTIL_SESSION_KEY = "MFA_OTP_LOCKED_UNTIL";
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int OTP_LOCK_MINUTES = 5;

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

        LocalDateTime lockedUntil = (LocalDateTime) session.getAttribute(MFA_OTP_LOCKED_UNTIL_SESSION_KEY);
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            return "redirect:/mfa-challenge?locked";
        }

        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaSecret() == null) {
            return "redirect:/login?error";
        }

        if (!totpService.verifyTotp(user.getMfaSecret(), otpCode)) {
            int attempts = readAttempts(session) + 1;
            if (attempts >= MAX_OTP_ATTEMPTS) {
                session.setAttribute(MFA_OTP_ATTEMPTS_SESSION_KEY, 0);
                session.setAttribute(MFA_OTP_LOCKED_UNTIL_SESSION_KEY, LocalDateTime.now().plusMinutes(OTP_LOCK_MINUTES));
                return "redirect:/mfa-challenge?locked";
            }
            session.setAttribute(MFA_OTP_ATTEMPTS_SESSION_KEY, attempts);
            return "redirect:/mfa-challenge?error";
        }

        session.setAttribute(MfaEnforcementFilter.MFA_VERIFIED_SESSION_KEY, true);
        session.removeAttribute(MFA_OTP_ATTEMPTS_SESSION_KEY);
        session.removeAttribute(MFA_OTP_LOCKED_UNTIL_SESSION_KEY);
        return "redirect:/dashboard";
    }

    private int readAttempts(HttpSession session) {
        Object raw = session.getAttribute(MFA_OTP_ATTEMPTS_SESSION_KEY);
        if (raw instanceof Integer value) {
            return value;
        }
        return 0;
    }
}
