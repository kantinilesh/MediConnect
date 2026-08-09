package com.mediconnect.notification;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring {@link ApplicationEvent} published after an appointment lifecycle change.
 *
 * <p>Published by {@link com.mediconnect.service.impl.AppointmentServiceImpl} via
 * {@link org.springframework.context.ApplicationEventPublisher}. The booking service
 * knows nothing about the dispatcher internals — it just fires this event and returns.
 *
 * <p><b>Thread safety</b>: all fields are final, set in the constructor.
 * The event object is safely published to any number of listener threads via the
 * Spring {@code ApplicationEventMulticaster}, which provides the necessary
 * happens-before guarantee through its synchronisation.
 */
@Getter
public class AppointmentNotificationEvent extends ApplicationEvent {

    public enum EventType {
        APPOINTMENT_BOOKED,
        APPOINTMENT_CANCELLED,
        APPOINTMENT_RESCHEDULED,
        APPOINTMENT_REMINDER
    }

    private final EventType eventType;
    private final UUID appointmentId;

    // Recipient info (denormalised — avoids a DB round-trip in the worker thread)
    private final String patientEmail;
    private final String patientName;
    private final String doctorName;
    private final String doctorSpecialization;
    private final String appointmentDate;   // ISO-8601 string
    private final String startTime;         // HH:mm
    private final String clinicName;
    private final Instant publishedAt;

    public AppointmentNotificationEvent(
            Object source,
            EventType eventType,
            UUID appointmentId,
            String patientEmail,
            String patientName,
            String doctorName,
            String doctorSpecialization,
            String appointmentDate,
            String startTime,
            String clinicName) {
        super(source);
        this.eventType          = eventType;
        this.appointmentId      = appointmentId;
        this.patientEmail       = patientEmail;
        this.patientName        = patientName;
        this.doctorName         = doctorName;
        this.doctorSpecialization = doctorSpecialization;
        this.appointmentDate    = appointmentDate;
        this.startTime          = startTime;
        this.clinicName         = clinicName;
        this.publishedAt        = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("AppointmentNotificationEvent{type=%s, appointmentId=%s, to=%s}",
                eventType, appointmentId, patientEmail);
    }
}
