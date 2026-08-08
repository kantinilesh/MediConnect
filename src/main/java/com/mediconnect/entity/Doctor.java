package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Doctor entity — joined to {@link User} via JOINED inheritance.
 * Holds specialization, clinic information, and consultation details.
 *
 * <p>Future: extract {@code Clinic} as its own entity if multiple
 * doctors share the same clinic (Phase 2 decision — see PROJECT_CONTEXT.md A5).
 */
@Entity
@Table(
    name = "doctors",
    indexes = {
        @Index(name = "idx_doctor_specialization", columnList = "specialization")
    }
)
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Doctor extends User {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Specialization specialization;

    @Column(columnDefinition = "TEXT")
    private String qualifications;

    @Column(length = 255)
    private String clinicName;

    @Column(length = 512)
    private String clinicAddress;

    @Column
    private Integer yearsOfExperience;

    @Column(precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * Cached aggregate rating — updated by a scheduled job / trigger in Phase 6.
     */
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    // ── Relationships ─────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Availability> availabilities = new ArrayList<>();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Appointment> appointments = new ArrayList<>();

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum Specialization {
        GENERAL_PRACTICE,
        CARDIOLOGY,
        DERMATOLOGY,
        NEUROLOGY,
        ORTHOPEDICS,
        PEDIATRICS,
        PSYCHIATRY,
        RADIOLOGY,
        ONCOLOGY,
        GYNECOLOGY,
        OPHTHALMOLOGY,
        ENT,
        ENDOCRINOLOGY,
        GASTROENTEROLOGY,
        NEPHROLOGY,
        PULMONOLOGY,
        RHEUMATOLOGY,
        UROLOGY,
        DENTISTRY,
        OTHER
    }
}
