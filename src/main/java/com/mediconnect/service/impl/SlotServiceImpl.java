package com.mediconnect.service.impl;

import com.mediconnect.dto.availability.AvailabilityRequestDto;
import com.mediconnect.dto.availability.AvailabilityResponseDto;
import com.mediconnect.dto.slot.SlotResponseDto;
import com.mediconnect.entity.Availability;
import com.mediconnect.entity.Availability.DayOfWeek;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Slot;
import com.mediconnect.exception.ResourceNotFoundException;
import com.mediconnect.repository.AvailabilityRepository;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.SlotRepository;
import com.mediconnect.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotServiceImpl implements SlotService {

    private final DoctorRepository doctorRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SlotRepository slotRepository;

    @Override
    @Transactional
    public AvailabilityResponseDto createAvailability(UUID doctorId, AvailabilityRequestDto request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        Availability availability = Availability.builder()
                .doctor(doctor)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .slotDurationMinutes(request.getSlotDurationMinutes())
                .isActive(true)
                .build();

        Availability saved = availabilityRepository.save(availability);
        return AvailabilityResponseDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailabilityResponseDto> getDoctorAvailabilities(UUID doctorId) {
        return availabilityRepository.findByDoctorIdAndIsActiveTrue(doctorId)
                .stream()
                .map(AvailabilityResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<SlotResponseDto> generateSlots(UUID doctorId, LocalDate startDate, LocalDate endDate) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));

        List<Availability> availabilities = availabilityRepository.findByDoctorIdAndIsActiveTrue(doctorId);
        List<Slot> createdSlots = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DayOfWeek currentDayOfWeek = DayOfWeek.valueOf(date.getDayOfWeek().name());

            for (Availability avail : availabilities) {
                if (avail.getDayOfWeek() == currentDayOfWeek) {
                    LocalTime slotStart = avail.getStartTime();

                    while (slotStart.plusMinutes(avail.getSlotDurationMinutes()).isBefore(avail.getEndTime())
                            || slotStart.plusMinutes(avail.getSlotDurationMinutes()).equals(avail.getEndTime())) {

                        LocalTime slotEnd = slotStart.plusMinutes(avail.getSlotDurationMinutes());

                        // Check if slot already exists in DB to prevent duplicates
                        if (!slotRepository.existsByDoctorIdAndSlotDateAndStartTime(doctorId, date, slotStart)) {
                            Slot slot = Slot.builder()
                                    .doctor(doctor)
                                    .slotDate(date)
                                    .startTime(slotStart)
                                    .endTime(slotEnd)
                                    .status(Slot.Status.AVAILABLE)
                                    .build();

                            createdSlots.add(slotRepository.save(slot));
                        }
                        slotStart = slotEnd;
                    }
                }
            }
        }

        log.info("Generated {} new slots for doctor {} between {} and {}",
                createdSlots.size(), doctorId, startDate, endDate);

        return createdSlots.stream()
                .map(SlotResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponseDto> getAvailableSlots(UUID doctorId, LocalDate date) {
        return slotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                        doctorId, date, Slot.Status.AVAILABLE)
                .stream()
                .map(SlotResponseDto::fromEntity)
                .collect(Collectors.toList());
    }
}
