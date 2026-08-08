package com.mediconnect.integration;

import com.mediconnect.dto.appointment.BookAppointmentRequestDto;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.Slot;
import com.mediconnect.exception.SlotAlreadyBookedException;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.SlotRepository;
import com.mediconnect.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test for verifying double-booking prevention under high concurrency.
 *
 * <p>Uses Testcontainers with a real MySQL 8 container when Docker is running,
 * or falls back to the embedded H2 MySQL compatibility mode if Docker is inactive.
 */
@SpringBootTest
@ActiveProfiles("test")
class AppointmentConcurrencyIntegrationTest {

    static MySQLContainer<?> mysql;

    static {
        if (isDockerAvailable()) {
            mysql = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("mediconnect_test_db")
                    .withUsername("test_user")
                    .withPassword("test_pass");
            mysql.start();
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (mysql != null && mysql.isRunning()) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        }
    }

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private Doctor doctor;
    private Slot slot;
    private List<Patient> patients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        slotRepository.deleteAll();
        patientRepository.deleteAll();
        doctorRepository.deleteAll();

        patients.clear();

        // 1. Create Doctor
        doctor = Doctor.builder()
                .email("dr.cardio@mediconnect.com")
                .passwordHash("$2a$12$e/a/b/c/d/e/f")
                .role(Doctor.Role.DOCTOR)
                .firstName("Sarah")
                .lastName("Conner")
                .specialization(Doctor.Specialization.CARDIOLOGY)
                .clinicName("Heart Clinic")
                .consultationFee(BigDecimal.valueOf(150.00))
                .enabled(true)
                .build();
        doctor = doctorRepository.save(doctor);

        // 2. Create 10 Patients
        for (int i = 1; i <= 10; i++) {
            Patient patient = Patient.builder()
                    .email("patient" + i + "@mediconnect.com")
                    .passwordHash("$2a$12$e/a/b/c/d/e/f")
                    .role(Patient.Role.PATIENT)
                    .firstName("Patient")
                    .lastName("Num" + i)
                    .enabled(true)
                    .build();
            patients.add(patientRepository.save(patient));
        }

        // 3. Create 1 AVAILABLE Slot
        slot = Slot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .status(Slot.Status.AVAILABLE)
                .build();
        slot = slotRepository.save(slot);
    }

    @Test
    @DisplayName("Concurrently booking the exact same slot with 10 parallel threads yields EXACTLY 1 success and 9 failures")
    void testConcurrentBookingRaceCondition() throws Exception {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        final UUID targetSlotId = slot.getId();

        for (int i = 0; i < numberOfThreads; i++) {
            final Patient patient = patients.get(i);
            executorService.submit(() -> {
                try {
                    latch.await(); // wait for start signal so all threads fire simultaneously
                    BookAppointmentRequestDto request = BookAppointmentRequestDto.builder()
                            .patientId(patient.getId())
                            .slotId(targetSlotId)
                            .reason("Routine checkup")
                            .build();

                    appointmentService.bookAppointment(request);
                    successCount.incrementAndGet();
                } catch (SlotAlreadyBookedException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        latch.countDown();

        // Wait for all 10 threads to finish
        boolean completedInTime = completionLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(completedInTime, "Concurrent booking threads should complete within 15 seconds");

        // Verification Assertions
        assertEquals(1, successCount.get(), "EXACTLY ONE patient must successfully book the slot");
        assertEquals(9, failureCount.get(), "EXACTLY 9 patients must fail with SlotAlreadyBookedException");

        // Database assertions
        Slot updatedSlot = slotRepository.findById(targetSlotId).orElseThrow();
        assertEquals(Slot.Status.BOOKED, updatedSlot.getStatus(), "Slot status in DB must be BOOKED");

        long appointmentCount = appointmentRepository.count();
        assertEquals(1, appointmentCount, "Database must contain EXACTLY ONE appointment record");
    }
}
