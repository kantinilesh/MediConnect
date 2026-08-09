package com.mediconnect.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Unified response DTO for login and registration operations.
 * Contains the JWT access token, refresh token, and minimal user info.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private UUID   userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
}
