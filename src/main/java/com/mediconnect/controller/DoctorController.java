package com.mediconnect.controller;

import com.mediconnect.dto.common.ApiResponse;
import com.mediconnect.dto.doctor.DoctorResponseDto;
import com.mediconnect.dto.doctor.DoctorSearchCriteria;
import com.mediconnect.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Doctor discovery — search and view doctor profiles.
 *
 * <h3>RBAC:</h3>
 * <ul>
 *   <li>Public (no JWT required) — GET /search and GET /{id} are open for unauthenticated browsing.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctor Discovery", description = "Endpoints for searching and viewing doctor profiles (public)")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping("/search")
    @Operation(summary = "Search and filter doctors with pagination — public, no auth required")
    public ResponseEntity<ApiResponse<Page<DoctorResponseDto>>> searchDoctors(
            @ModelAttribute DoctorSearchCriteria criteria) {
        Page<DoctorResponseDto> doctors = doctorService.searchDoctors(criteria);
        return ResponseEntity.ok(ApiResponse.success(doctors));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get doctor profile by ID — public, no auth required")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Doctor profile found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Doctor not found", content = @Content(schema = @Schema(implementation = com.mediconnect.dto.common.ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<DoctorResponseDto>> getDoctorById(@PathVariable UUID id) {
        DoctorResponseDto doctor = doctorService.getDoctorById(id);
        return ResponseEntity.ok(ApiResponse.success(doctor));
    }
}
