package com.mediconnect.controller;

import com.mediconnect.dto.appointment.AppointmentResponseDto;
import com.mediconnect.dto.appointment.BookAppointmentRequestDto;
import com.mediconnect.dto.appointment.CancelAppointmentRequestDto;
import com.mediconnect.dto.appointment.RescheduleAppointmentRequestDto;
import com.mediconnect.dto.common.ApiResponse;
import com.mediconnect.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Appointment booking, cancellation, rescheduling, and listing.
 *
 * <h3>RBAC:</h3>
 * <ul>
 *   <li>PATIENT — book, cancel/reschedule own appointments, view own list.</li>
 *   <li>DOCTOR  — view appointments for their schedule.</li>
 *   <li>ADMIN   — view/cancel any appointment.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointment Booking", description = "Endpoints for booking, cancelling, rescheduling, and listing appointments")
@SecurityRequirement(name = "bearerAuth")
public class AppointmentController {

    private final AppointmentService appointmentService;

    // PATIENT books a slot
    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Book an available slot atomically (Pessimistic Locking) — PATIENT only")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> bookAppointment(
            @Valid @RequestBody BookAppointmentRequestDto request) {
        AppointmentResponseDto appointment = appointmentService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Appointment booked successfully", appointment));
    }

    // PATIENT or ADMIN can cancel
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    @Operation(summary = "Cancel an appointment and free the slot — PATIENT (own) or ADMIN")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> cancelAppointment(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelAppointmentRequestDto request) {
        AppointmentResponseDto appointment = appointmentService.cancelAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Appointment cancelled successfully", appointment));
    }

    // PATIENT or ADMIN can reschedule
    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    @Operation(summary = "Reschedule an appointment to a new slot — PATIENT (own) or ADMIN")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> rescheduleAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleAppointmentRequestDto request) {
        AppointmentResponseDto appointment = appointmentService.rescheduleAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.success("Appointment rescheduled successfully", appointment));
    }

    // Any authenticated user can look up a single appointment by ID
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get appointment details by ID — authenticated users")
    public ResponseEntity<ApiResponse<AppointmentResponseDto>> getAppointmentById(@PathVariable UUID id) {
        AppointmentResponseDto appointment = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    // PATIENT views own appointments; ADMIN may view any patient's
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    @Operation(summary = "List appointments for a patient — PATIENT (own) or ADMIN")
    public ResponseEntity<ApiResponse<Page<AppointmentResponseDto>>> getPatientAppointments(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AppointmentResponseDto> appointments = appointmentService.getPatientAppointments(patientId, pageable);
        return ResponseEntity.ok(ApiResponse.success(appointments));
    }

    // DOCTOR views own schedule; ADMIN may view any doctor's
    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(summary = "List appointments for a doctor on a specific date — DOCTOR (own) or ADMIN")
    public ResponseEntity<ApiResponse<Page<AppointmentResponseDto>>> getDoctorAppointments(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startTime"));
        Page<AppointmentResponseDto> appointments = appointmentService.getDoctorAppointments(doctorId, date, pageable);
        return ResponseEntity.ok(ApiResponse.success(appointments));
    }
}
