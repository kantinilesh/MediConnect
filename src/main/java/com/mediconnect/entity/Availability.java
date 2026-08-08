package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Availability — slot template for a {@link Doctor}.
 *
 * <p>Defines the recurring windows during which a doctor is available.
 * Concrete bookable slots are derived at request time (template-based model —
 * see PROJECT_CONTEXT.md A3).
 *
 * <p>Composite index on {@code (doctorId, dayOfWeek)} accelerates the
 * slot-generation query during appointment booking.
 */
@Entity
@Table(
    name = "availabilities",
    indexes = {
        @Index(name = "idx_avail_doctor_day", columnList = "doctor_id, day_of_week")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /**
     * Duration of a single bookable slot in minutes (e.g. 30).
     */
    @Column(nullable = false)
    private Integer slotDurationMinutes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum DayOfWeek {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    }
}
