package com.mediconnect.controller;

import com.mediconnect.dto.auth.*;
import com.mediconnect.dto.common.ApiResponse;
import com.mediconnect.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints — no JWT required.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/register/patient} — Register a new patient account.</li>
 *   <li>{@code POST /api/v1/auth/register/doctor}  — Register a new doctor account.</li>
 *   <li>{@code POST /api/v1/auth/login}            — Authenticate and receive access + refresh tokens.</li>
 *   <li>{@code POST /api/v1/auth/refresh}          — Exchange a refresh token for a new access token.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, and token refresh endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/patient")
    @Operation(summary = "Register a new patient account")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Patient registered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or email already exists", content = @Content(schema = @Schema(implementation = com.mediconnect.dto.common.ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AuthResponseDto>> registerPatient(
            @Valid @RequestBody RegisterPatientRequestDto request) {
        AuthResponseDto response = authService.registerPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Patient registered successfully", response));
    }

    @PostMapping("/register/doctor")
    @Operation(summary = "Register a new doctor account")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Doctor registered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or email already exists", content = @Content(schema = @Schema(implementation = com.mediconnect.dto.common.ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AuthResponseDto>> registerDoctor(
            @Valid @RequestBody RegisterDoctorRequestDto request) {
        AuthResponseDto response = authService.registerDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Doctor registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password — returns access + refresh tokens")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Bad credentials", content = @Content(schema = @Schema(implementation = com.mediconnect.dto.common.ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new access token")
    public ResponseEntity<ApiResponse<AuthResponseDto>> refresh(
            @Valid @RequestBody TokenRefreshRequestDto request) {
        AuthResponseDto response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }
}
