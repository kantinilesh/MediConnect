package com.mediconnect.repository;

import com.mediconnect.entity.Notification;
import com.mediconnect.entity.Notification.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Notification}.
 *
 * <p>The dispatcher (Phase 4) will poll via
 * {@link #findByStatusAndScheduledAtBefore} — backed by the composite index
 * {@code idx_notif_scheduled_status}.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdAndStatus(UUID userId, NotificationStatus status, Pageable pageable);

    /**
     * Dispatcher poll: returns PENDING notifications whose scheduled time is
     * in the past (i.e. ready to send).
     */
    List<Notification> findByStatusAndScheduledAtBefore(NotificationStatus status, Instant before);
}
