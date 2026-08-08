package com.mediconnect.controller;

import com.mediconnect.dto.common.ApiResponse;
import com.mediconnect.dto.doctor.DoctorResponseDto;
import com.mediconnect.dto.doctor.DoctorSearchCriteria;
import com.mediconnect.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctor Discovery", description = "Endpoints for searching and viewing doctor profiles")
public class DoctorController {

    private final DoctorService doctorService;

    @GetMapping("/search")
    @Operation(summary = "Search and filter doctors with pagination")
    public ResponseEntity<ApiResponse<Page<DoctorResponseDto>>> searchDoctors(
            @ModelAttribute DoctorSearchCriteria criteria) {
        Page<DoctorResponseDto> doctors = doctorService.searchDoctors(criteria);
        return ResponseEntity.ok(ApiResponse.success(doctors));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get doctor profile by ID")
    public ResponseEntity<ApiResponse<DoctorResponseDto>> getDoctorById(@PathVariable UUID id) {
        DoctorResponseDto doctor = doctorService.getDoctorById(id);
        return ResponseEntity.ok(ApiResponse.success(doctor));
    }
}
