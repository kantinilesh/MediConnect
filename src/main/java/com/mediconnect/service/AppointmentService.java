package com.mediconnect.service;

import com.mediconnect.dto.appointment.AppointmentResponseDto;
import com.mediconnect.dto.appointment.BookAppointmentRequestDto;
import com.mediconnect.dto.appointment.CancelAppointmentRequestDto;
import com.mediconnect.dto.appointment.RescheduleAppointmentRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface AppointmentService {

    AppointmentResponseDto bookAppointment(BookAppointmentRequestDto request);

    AppointmentResponseDto cancelAppointment(UUID appointmentId, CancelAppointmentRequestDto request);

    AppointmentResponseDto rescheduleAppointment(UUID appointmentId, RescheduleAppointmentRequestDto request);

    Page<AppointmentResponseDto> getPatientAppointments(UUID patientId, Pageable pageable);

    Page<AppointmentResponseDto> getDoctorAppointments(UUID doctorId, LocalDate date, Pageable pageable);

    AppointmentResponseDto getAppointmentById(UUID appointmentId);
}
