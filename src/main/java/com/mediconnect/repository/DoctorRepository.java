package com.mediconnect.repository;

import com.mediconnect.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Doctor}.
 * Discovery queries (by specialization, etc.) will be expanded in Phase 2.
 */
@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Page<Doctor> findBySpecialization(Doctor.Specialization specialization, Pageable pageable);

    List<Doctor> findByEnabledTrue();
}
