package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Slot — represents a concrete bookable time window for a {@link Doctor}.
 *
 * <p>Slots are generated either from recurring {@link Availability} templates
 * or manually created as one-off slots.
 *
 * <p>Unique constraint {@code uk_doctor_date_time} on {@code (doctor_id, slot_date, start_time)}
 * provides database-level defense-in-depth against double-creation/booking.
 *
 * <p>Note: explicit getters are provided in addition to Lombok's {@code @Getter}
 * to ensure they are visible to the Java compiler regardless of annotation-processor order.
 */
@Entity
@Table(
    name = "slots",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_doctor_date_time",
            columnNames = {"doctor_id", "slot_date", "start_time"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.AVAILABLE;

    @Version
    private Long version;

    // ── Explicit accessors (complement Lombok @Getter for compiler reliability) ─

    public UUID getId()            { return id; }
    public Doctor getDoctor()      { return doctor; }
    public LocalDate getSlotDate() { return slotDate; }
    public LocalTime getStartTime(){ return startTime; }
    public LocalTime getEndTime()  { return endTime; }
    public Status getStatus()      { return status; }
    public Long getVersion()       { return version; }

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum Status {
        AVAILABLE, BOOKED, BLOCKED
    }
}
