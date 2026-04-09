package com.example.beacon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignAgentOwnerRequest {

    /** null 이면 소유자 해제 */
    private Long userId;
}
