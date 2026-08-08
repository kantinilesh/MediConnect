package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Appointment — the central booking entity.
 *
 * <p>Composite indexes:
 * <ul>
 *   <li>{@code (doctorId, appointmentDate, status)} — conflict detection and
 *       doctor's daily schedule queries.</li>
 *   <li>{@code (patientId, status)} — patient appointment history.</li>
 * </ul>
 */
@Entity
@Table(
    name = "appointments",
    indexes = {
        @Index(name = "idx_appt_doctor_date_status",
               columnList = "doctor_id, appointment_date, status"),
        @Index(name = "idx_appt_patient_status",
               columnList = "patient_id, status")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    /** Chief complaint provided by the patient at booking. */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /** Doctor's post-consultation notes (filled after the appointment). */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum Status {
        PENDING, CONFIRMED, CANCELLED, COMPLETED
    }
}
