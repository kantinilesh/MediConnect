package com.mediconnect.repository;

import com.mediconnect.entity.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Slot}.
 *
 * <p>Includes {@link #findByIdWithLock(UUID)} using {@link LockModeType#PESSIMISTIC_WRITE}
 * for atomic slot booking to prevent double-booking race conditions.
 */
@Repository
public interface SlotRepository extends JpaRepository<Slot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdWithLock(@Param("id") UUID id);

    List<Slot> findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
            UUID doctorId, LocalDate slotDate, Slot.Status status);

    List<Slot> findByDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            UUID doctorId, LocalDate startDate, LocalDate endDate);

    boolean existsByDoctorIdAndSlotDateAndStartTime(
            UUID doctorId, LocalDate slotDate, LocalTime startTime);
}
