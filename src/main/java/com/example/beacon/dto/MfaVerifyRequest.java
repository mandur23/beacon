package com.example.beacon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerifyRequest {
    private String tempToken;
    private String otpCode;
}
