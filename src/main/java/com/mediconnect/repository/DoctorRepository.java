package com.mediconnect.repository;

import com.mediconnect.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Doctor}.
 * Supports multi-criteria discovery and search.
 */
@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Page<Doctor> findBySpecialization(Doctor.Specialization specialization, Pageable pageable);

    List<Doctor> findByEnabledTrue();

    /**
     * Discovery API query: search/filter doctors by optional criteria.
     */
    @Query("""
            SELECT DISTINCT d FROM Doctor d
            WHERE d.enabled = true
              AND (:specialization IS NULL OR d.specialization = :specialization)
              AND (:name IS NULL OR LOWER(d.firstName) LIKE LOWER(CONCAT('%', :name, '%'))
                                 OR LOWER(d.lastName)  LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:location IS NULL OR LOWER(d.clinicAddress) LIKE LOWER(CONCAT('%', :location, '%'))
                                     OR LOWER(d.clinicName)    LIKE LOWER(CONCAT('%', :location, '%')))
              AND (:minRating IS NULL OR d.rating >= :minRating)
              AND (:availableDate IS NULL OR EXISTS (
                    SELECT s FROM Slot s
                    WHERE s.doctor.id = d.id
                      AND s.slotDate = :availableDate
                      AND s.status = 'AVAILABLE'
                  ))
            """)
    Page<Doctor> searchDoctors(
            @Param("specialization") Doctor.Specialization specialization,
            @Param("name")           String name,
            @Param("location")       String location,
            @Param("minRating")      BigDecimal minRating,
            @Param("availableDate")   LocalDate availableDate,
            Pageable pageable
    );
}
