package com.mediconnect.controller;

import com.mediconnect.dto.availability.AvailabilityRequestDto;
import com.mediconnect.dto.availability.AvailabilityResponseDto;
import com.mediconnect.dto.common.ApiResponse;
import com.mediconnect.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors/{doctorId}/availabilities")
@RequiredArgsConstructor
@Tag(name = "Doctor Availability", description = "Endpoints for managing doctor availability templates")
public class AvailabilityController {

    private final SlotService slotService;

    @PostMapping
    @Operation(summary = "Define recurring availability template for a doctor")
    public ResponseEntity<ApiResponse<AvailabilityResponseDto>> createAvailability(
            @PathVariable UUID doctorId,
            @Valid @RequestBody AvailabilityRequestDto request) {
        AvailabilityResponseDto response = slotService.createAvailability(doctorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Availability template created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all availability templates for a doctor")
    public ResponseEntity<ApiResponse<List<AvailabilityResponseDto>>> getDoctorAvailabilities(
            @PathVariable UUID doctorId) {
        List<AvailabilityResponseDto> response = slotService.getDoctorAvailabilities(doctorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
