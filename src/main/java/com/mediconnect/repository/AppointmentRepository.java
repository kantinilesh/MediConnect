package com.mediconnect.repository;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Appointment.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Appointment}.
 *
 * <p>Key queries are backed by the composite indexes declared on the entity:
 * <ul>
 *   <li>{@code idx_appt_doctor_date_status} — used by conflict detection.</li>
 *   <li>{@code idx_appt_patient_status} — used by patient history.</li>
 * </ul>
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Page<Appointment> findByPatientIdAndStatus(UUID patientId, Status status, Pageable pageable);

    Page<Appointment> findByDoctorIdAndAppointmentDate(UUID doctorId, LocalDate date, Pageable pageable);

    /**
     * Conflict detection: finds any non-cancelled appointment for the given
     * doctor on the given date that overlaps the requested time window.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.appointmentDate = :date
              AND a.status <> 'CANCELLED'
              AND a.startTime < :endTime
              AND a.endTime   > :startTime
            """)
    List<Appointment> findConflicting(
            @Param("doctorId")  UUID doctorId,
            @Param("date")      LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime")   LocalTime endTime
    );
}
