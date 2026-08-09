package com.mediconnect.service.impl;

import com.mediconnect.dto.appointment.AppointmentResponseDto;
import com.mediconnect.dto.appointment.BookAppointmentRequestDto;
import com.mediconnect.dto.appointment.CancelAppointmentRequestDto;
import com.mediconnect.dto.appointment.RescheduleAppointmentRequestDto;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.Slot;
import com.mediconnect.exception.InvalidAppointmentOperationException;
import com.mediconnect.exception.ResourceNotFoundException;
import com.mediconnect.exception.SlotAlreadyBookedException;
import com.mediconnect.notification.AppointmentNotificationEvent;
import com.mediconnect.notification.AppointmentNotificationEvent.EventType;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.SlotRepository;
import com.mediconnect.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository     appointmentRepository;
    private final SlotRepository            slotRepository;
    private final PatientRepository         patientRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AppointmentResponseDto bookAppointment(BookAppointmentRequestDto request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId()));

        // 1. Acquire PESSIMISTIC WRITE lock on the target Slot row to serialize concurrent booking attempts
        Slot slot = slotRepository.findByIdWithLock(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot", "id", request.getSlotId()));

        // 2. Service-level check: Verify slot is AVAILABLE
        if (slot.getStatus() != Slot.Status.AVAILABLE) {
            throw new SlotAlreadyBookedException(slot.getId());
        }

        Doctor doctor = slot.getDoctor();

        // 3. Additional safety check: verify no active overlapping appointment for this doctor at this date/time
        List<Appointment> conflicts = appointmentRepository.findConflicting(
                doctor.getId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime());

        if (!conflicts.isEmpty()) {
            throw new SlotAlreadyBookedException("Doctor already has an active appointment at this date and time");
        }

        // 4. Mark slot as BOOKED
        slot.setStatus(Slot.Status.BOOKED);
        slotRepository.save(slot);

        // 5. Create Appointment record
        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .slot(slot)
                .appointmentDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(Appointment.Status.CONFIRMED)
                .reason(request.getReason())
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment '{}' successfully booked for patient '{}' with doctor '{}' on {}",
                saved.getId(), patient.getId(), doctor.getId(), slot.getSlotDate());

        // Publish notification event — the dispatcher receives this after TX commits
        // (via @TransactionalEventListener). The booking flow knows nothing about dispatch.
        eventPublisher.publishEvent(buildEvent(this, EventType.APPOINTMENT_BOOKED, saved, doctor, patient));

        return AppointmentResponseDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public AppointmentResponseDto cancelAppointment(UUID appointmentId, CancelAppointmentRequestDto request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (appointment.getStatus() == Appointment.Status.CANCELLED) {
            throw new InvalidAppointmentOperationException("Appointment is already cancelled");
        }
        if (appointment.getStatus() == Appointment.Status.COMPLETED) {
            throw new InvalidAppointmentOperationException("Cannot cancel a completed appointment");
        }

        // Mark appointment CANCELLED
        appointment.setStatus(Appointment.Status.CANCELLED);
        if (request != null && request.getCancellationReason() != null) {
            appointment.setCancellationReason(request.getCancellationReason());
        }

        // Release slot back to AVAILABLE
        Slot slot = appointment.getSlot();
        if (slot != null) {
            slot.setStatus(Slot.Status.AVAILABLE);
            slotRepository.save(slot);
        }

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment '{}' cancelled successfully", appointmentId);

        // Notify patient of cancellation
        eventPublisher.publishEvent(buildEvent(this, EventType.APPOINTMENT_CANCELLED,
                saved, saved.getDoctor(), saved.getPatient()));

        return AppointmentResponseDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public AppointmentResponseDto rescheduleAppointment(UUID appointmentId, RescheduleAppointmentRequestDto request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (appointment.getStatus() == Appointment.Status.CANCELLED || appointment.getStatus() == Appointment.Status.COMPLETED) {
            throw new InvalidAppointmentOperationException("Cannot reschedule a cancelled or completed appointment");
        }

        // Lock new slot with PESSIMISTIC_WRITE
        Slot newSlot = slotRepository.findByIdWithLock(request.getNewSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot", "id", request.getNewSlotId()));

        if (newSlot.getStatus() != Slot.Status.AVAILABLE) {
            throw new SlotAlreadyBookedException(newSlot.getId());
        }

        // Free old slot
        Slot oldSlot = appointment.getSlot();
        if (oldSlot != null) {
            oldSlot.setStatus(Slot.Status.AVAILABLE);
            slotRepository.save(oldSlot);
        }

        // Book new slot
        newSlot.setStatus(Slot.Status.BOOKED);
        slotRepository.save(newSlot);

        // Update appointment
        appointment.setSlot(newSlot);
        appointment.setDoctor(newSlot.getDoctor());
        appointment.setAppointmentDate(newSlot.getSlotDate());
        appointment.setStartTime(newSlot.getStartTime());
        appointment.setEndTime(newSlot.getEndTime());

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment '{}' rescheduled to slot '{}' on {}", appointmentId, newSlot.getId(), newSlot.getSlotDate());

        // Notify patient of reschedule
        eventPublisher.publishEvent(buildEvent(this, EventType.APPOINTMENT_RESCHEDULED,
                saved, saved.getDoctor(), saved.getPatient()));

        return AppointmentResponseDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponseDto> getPatientAppointments(UUID patientId, Pageable pageable) {
        return appointmentRepository.findByPatientIdAndStatus(patientId, Appointment.Status.CONFIRMED, pageable)
                .map(AppointmentResponseDto::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponseDto> getDoctorAppointments(UUID doctorId, LocalDate date, Pageable pageable) {
        return appointmentRepository.findByDoctorIdAndAppointmentDate(doctorId, date, pageable)
                .map(AppointmentResponseDto::fromEntity);
    }

    // ── Event factory ─────────────────────────────────────────────────────────

    /**
     * Builds an {@link AppointmentNotificationEvent} from JPA entities.
     * All data is copied out eagerly (denormalised) so that the dispatcher's
     * worker threads never need to touch a JPA EntityManager.
     */
    private static AppointmentNotificationEvent buildEvent(
            Object source, EventType type, Appointment appt, Doctor doctor, Patient patient) {
        String clinicName = (doctor.getClinicName() != null) ? doctor.getClinicName() : "MediConnect Clinic";
        String doctorName = "Dr. " + doctor.getFirstName() + " " + doctor.getLastName();
        String patientName = patient.getFirstName() + " " + patient.getLastName();
        return new AppointmentNotificationEvent(
                source,
                type,
                appt.getId(),
                patient.getEmail(),
                patientName,
                doctorName,
                doctor.getSpecialization() != null ? doctor.getSpecialization().name() : "",
                appt.getAppointmentDate() != null ? appt.getAppointmentDate().toString() : "",
                appt.getStartTime() != null ? appt.getStartTime().toString() : "",
                clinicName);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponseDto getAppointmentById(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));
        return AppointmentResponseDto.fromEntity(appointment);
    }
}
