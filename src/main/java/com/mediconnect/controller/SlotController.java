package com.mediconnect.controller;

import com.mediconnect.dto.common.ApiResponse;
import com.mediconnect.dto.slot.SlotGenerateRequestDto;
import com.mediconnect.dto.slot.SlotResponseDto;
import com.mediconnect.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Slot generation and availability querying.
 *
 * <h3>RBAC:</h3>
 * <ul>
 *   <li>DOCTOR — generate slots from own availability templates.</li>
 *   <li>PATIENT / DOCTOR / ADMIN — view available slots for a doctor on a date.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/doctors/{doctorId}/slots")
@RequiredArgsConstructor
@Tag(name = "Slot Management", description = "Endpoints for slot generation and querying bookable slots")
@SecurityRequirement(name = "bearerAuth")
public class SlotController {

    private final SlotService slotService;

    // DOCTOR generates own slots
    @PostMapping("/generate")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Generate bookable slots for a date range from doctor's availability templates — DOCTOR only")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Slots successfully generated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range", content = @Content(schema = @Schema(implementation = com.mediconnect.dto.common.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User does not have DOCTOR role or is generating for another doctor", content = @Content(schema = @Schema(implementation = com.mediconnect.dto.common.ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<SlotResponseDto>>> generateSlots(
            @PathVariable UUID doctorId,
            @Valid @RequestBody SlotGenerateRequestDto request) {
        List<SlotResponseDto> slots = slotService.generateSlots(doctorId, request.getStartDate(), request.getEndDate());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(String.format("Generated %d slots", slots.size()), slots));
    }

    // Any authenticated user can view available slots
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get available slots for a doctor on a specific date — authenticated users")
    public ResponseEntity<ApiResponse<List<SlotResponseDto>>> getAvailableSlots(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<SlotResponseDto> slots = slotService.getAvailableSlots(doctorId, date);
        return ResponseEntity.ok(ApiResponse.success(slots));
    }
}
