package com.mediconnect.dto.appointment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO to reschedule an existing appointment to a new slot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleAppointmentRequestDto {

    @NotNull(message = "New Slot ID is required")
    private UUID newSlotId;
}
