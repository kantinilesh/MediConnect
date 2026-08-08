package com.mediconnect.dto.appointment;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to cancel an existing appointment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelAppointmentRequestDto {

    @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters")
    private String cancellationReason;
}
