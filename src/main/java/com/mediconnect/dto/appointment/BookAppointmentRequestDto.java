package com.mediconnect.dto.appointment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO to book a slot for a patient.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookAppointmentRequestDto {

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    @NotNull(message = "Slot ID is required")
    private UUID slotId;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
