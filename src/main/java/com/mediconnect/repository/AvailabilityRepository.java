package com.mediconnect.repository;

import com.mediconnect.entity.Availability;
import com.mediconnect.entity.Availability.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Availability}.
 *
 * <p>Primary query is by {@code (doctorId, dayOfWeek)} — backed by the
 * composite index {@code idx_avail_doctor_day}.
 */
@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByDoctorIdAndDayOfWeekAndIsActiveTrue(UUID doctorId, DayOfWeek dayOfWeek);

    List<Availability> findByDoctorIdAndIsActiveTrue(UUID doctorId);
}
