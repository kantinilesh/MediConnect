package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification — record for every outbound communication event.
 *
 * <p>The {@code payload} JSON column is intentionally flexible so that the
 * dispatcher (Phase 4) can carry email templates, SMS bodies, or push
 * notification payloads without a schema migration.
 *
 * <p>Composite indexes:
 * <ul>
 *   <li>{@code (userId, status)} — user notification feed.</li>
 *   <li>{@code (scheduledAt, status)} — dispatcher polling for pending sends.</li>
 * </ul>
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notif_user_status",      columnList = "user_id, status"),
        @Index(name = "idx_notif_scheduled_status", columnList = "scheduled_at, status")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationEvent event;

    /**
     * Flexible JSON payload interpreted by the dispatcher.
     * Example: {@code {"to":"user@example.com","subject":"Appointment confirmed","body":"…"}}
     */
    @Column(columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    private Instant sentAt;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum NotificationType {
        EMAIL, SMS, PUSH
    }

    public enum NotificationEvent {
        APPOINTMENT_BOOKED,
        APPOINTMENT_CONFIRMED,
        APPOINTMENT_CANCELLED,
        APPOINTMENT_COMPLETED,
        REMINDER_24H,
        REMINDER_1H,
        ACCOUNT_CREATED,
        PASSWORD_RESET
    }

    public enum NotificationStatus {
        PENDING, SENT, FAILED
    }
}
