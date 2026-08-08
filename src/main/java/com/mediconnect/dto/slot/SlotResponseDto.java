package com.mediconnect.dto.slot;

import com.mediconnect.entity.Slot;
import com.mediconnect.entity.Slot.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Response DTO for a bookable Slot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotResponseDto {

    private UUID id;
    private UUID doctorId;
    private String doctorName;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Status status;

    public static SlotResponseDto fromEntity(Slot slot) {
        return SlotResponseDto.builder()
                .id(slot.getId())
                .doctorId(slot.getDoctor().getId())
                .doctorName("Dr. " + slot.getDoctor().getFirstName() + " " + slot.getDoctor().getLastName())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .build();
    }
}
