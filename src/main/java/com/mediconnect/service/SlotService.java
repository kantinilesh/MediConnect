package com.mediconnect.service;

import com.mediconnect.dto.availability.AvailabilityRequestDto;
import com.mediconnect.dto.availability.AvailabilityResponseDto;
import com.mediconnect.dto.slot.SlotResponseDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SlotService {

    AvailabilityResponseDto createAvailability(UUID doctorId, AvailabilityRequestDto request);

    List<AvailabilityResponseDto> getDoctorAvailabilities(UUID doctorId);

    List<SlotResponseDto> generateSlots(UUID doctorId, LocalDate startDate, LocalDate endDate);

    List<SlotResponseDto> getAvailableSlots(UUID doctorId, LocalDate date);
}
