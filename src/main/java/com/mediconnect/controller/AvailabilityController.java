package com.mediconnect.controller;

import com.mediconnect.dto.availability.AvailabilityRequestDto;
import com.mediconnect.dto.availability.AvailabilityResponseDto;
import com.mediconnect.dto.common.ApiResponse;
import com.mediconnect.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Doctor availability template management.
 *
 * <h3>RBAC:</h3>
 * <ul>
 *   <li>DOCTOR — define and view own availability templates.</li>
 *   <li>ADMIN  — view any doctor's templates (but cannot create on their behalf).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/doctors/{doctorId}/availabilities")
@RequiredArgsConstructor
@Tag(name = "Doctor Availability", description = "Endpoints for managing doctor availability templates")
@SecurityRequirement(name = "bearerAuth")
public class AvailabilityController {

    private final SlotService slotService;

    // DOCTOR defines own availability template
    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Define recurring availability template for a doctor — DOCTOR only")
    public ResponseEntity<ApiResponse<AvailabilityResponseDto>> createAvailability(
            @PathVariable UUID doctorId,
            @Valid @RequestBody AvailabilityRequestDto request) {
        AvailabilityResponseDto response = slotService.createAvailability(doctorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Availability template created successfully", response));
    }

    // DOCTOR or ADMIN can view templates
    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "Get all availability templates for a doctor — DOCTOR or ADMIN")
    public ResponseEntity<ApiResponse<List<AvailabilityResponseDto>>> getDoctorAvailabilities(
            @PathVariable UUID doctorId) {
        List<AvailabilityResponseDto> response = slotService.getDoctorAvailabilities(doctorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
