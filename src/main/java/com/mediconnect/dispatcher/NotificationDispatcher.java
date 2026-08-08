package com.mediconnect.dispatcher;

import com.mediconnect.entity.Notification;
import com.mediconnect.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * NotificationDispatcher — stub for Phase 0.
 *
 * <p>In Phase 4 this will be replaced with:
 * <ul>
 *   <li>An {@code @Scheduled} polling loop that reads PENDING notifications.</li>
 *   <li>Channel-specific senders (JavaMailSender for EMAIL, Twilio for SMS,
 *       Firebase for PUSH).</li>
 *   <li>Retry logic with exponential backoff.</li>
 *   <li>Dead-letter handling for FAILED notifications.</li>
 * </ul>
 *
 * <p>For now {@link #dispatch()} logs what it would send so the scaffolding
 * compiles and the contract is clear.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;

    /**
     * Entry point for the dispatcher.
     * Called by a scheduler (Phase 4) or directly from service layer.
     */
    public void dispatch() {
        List<Notification> pending = notificationRepository
                .findByStatusAndScheduledAtBefore(
                        Notification.NotificationStatus.PENDING,
                        Instant.now());

        log.info("[Dispatcher] Found {} pending notification(s) — stub, not sending yet.",
                pending.size());

        // TODO Phase 4: route each notification to the correct channel sender
        for (Notification n : pending) {
            log.debug("[Dispatcher] Would send {} / {} to user {}",
                    n.getType(), n.getEvent(), n.getUser().getId());
        }
    }
}
