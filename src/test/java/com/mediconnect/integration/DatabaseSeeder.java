package com.mediconnect.integration;

import com.mediconnect.entity.Doctor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-speed JDBC batch seeder.
 * Run this test once to populate the database with realistic volume.
 */
@SpringBootTest
@ActiveProfiles("local")
public class DatabaseSeeder {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    @Test
    //@Disabled("Run manually to seed DB")
    void seedDatabase() {
        log.info("Starting database seeding...");
        long start = System.currentTimeMillis();

        int numDoctors = 500;
        int numPatients = 50_000;
        int numAppointments = 200_000;

        List<UUID> doctorIds = new ArrayList<>();
        List<UUID> patientIds = new ArrayList<>();

        String pwd = passwordEncoder.encode("password");

        // 1. Seed Doctors
        log.info("Seeding {} Doctors...", numDoctors);
        jdbcTemplate.batchUpdate(
                "INSERT INTO users (id, email, password_hash, role, first_name, last_name, enabled, email_verified, created_at, updated_at) VALUES (?, ?, ?, 'DOCTOR', ?, ?, true, true, NOW(), NOW())",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        UUID id = UUID.randomUUID();
                        doctorIds.add(id);
                        ps.setBytes(1, uuidToBytes(id));
                        ps.setString(2, "doctor" + i + "@mediconnect.com");
                        ps.setString(3, pwd);
                        ps.setString(4, "DocFirst" + i);
                        ps.setString(5, "DocLast" + i);
                    }
                    @Override
                    public int getBatchSize() { return numDoctors; }
                }
        );

        Doctor.Specialization[] specs = Doctor.Specialization.values();
        jdbcTemplate.batchUpdate(
                "INSERT INTO doctors (user_id, specialization, clinic_name, clinic_address, years_of_experience, rating, consultation_fee) VALUES (?, ?, ?, ?, ?, ?, ?)",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setBytes(1, uuidToBytes(doctorIds.get(i)));
                        ps.setString(2, specs[i % specs.length].name());
                        ps.setString(3, "Clinic " + (i % 50));
                        ps.setString(4, "Address " + i);
                        ps.setInt(5, ThreadLocalRandom.current().nextInt(1, 30));
                        ps.setDouble(6, 3.5 + ThreadLocalRandom.current().nextDouble() * 1.5);
                        ps.setDouble(7, 100.0);
                    }
                    @Override
                    public int getBatchSize() { return numDoctors; }
                }
        );

        // 2. Seed Patients
        log.info("Seeding {} Patients...", numPatients);
        // Batch in chunks of 5000 to avoid memory issues
        for (int chunk = 0; chunk < numPatients / 5000; chunk++) {
            int offset = chunk * 5000;
            List<UUID> chunkIds = new ArrayList<>();
            for(int i=0; i<5000; i++) chunkIds.add(UUID.randomUUID());
            patientIds.addAll(chunkIds);

            jdbcTemplate.batchUpdate(
                    "INSERT INTO users (id, email, password_hash, role, first_name, last_name, enabled, email_verified, created_at, updated_at) VALUES (?, ?, ?, 'PATIENT', ?, ?, true, true, NOW(), NOW())",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                            ps.setBytes(1, uuidToBytes(chunkIds.get(i)));
                            ps.setString(2, "patient" + (offset + i) + "@mediconnect.com");
                            ps.setString(3, pwd);
                            ps.setString(4, "PatFirst" + (offset + i));
                            ps.setString(5, "PatLast" + (offset + i));
                        }
                        @Override
                        public int getBatchSize() { return 5000; }
                    }
            );

            jdbcTemplate.batchUpdate(
                    "INSERT INTO patients (user_id, date_of_birth) VALUES (?, ?)",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                            ps.setBytes(1, uuidToBytes(chunkIds.get(i)));
                            ps.setObject(2, LocalDate.of(1950 + ThreadLocalRandom.current().nextInt(50), 1, 1));
                        }
                        @Override
                        public int getBatchSize() { return 5000; }
                    }
            );
        }

        // 3. Seed Slots and Appointments
        log.info("Seeding {} Appointments...", numAppointments);
        int chunkSize = 10000;
        for (int chunk = 0; chunk < numAppointments / chunkSize; chunk++) {
            List<UUID> slotIds = new ArrayList<>();
            for(int i=0; i<chunkSize; i++) slotIds.add(UUID.randomUUID());
            
            jdbcTemplate.batchUpdate(
                    "INSERT IGNORE INTO slots (id, doctor_id, slot_date, start_time, end_time, status, version) VALUES (?, ?, ?, ?, ?, 'BOOKED', 0)",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                            UUID docId = doctorIds.get(ThreadLocalRandom.current().nextInt(numDoctors));
                            LocalDate date = LocalDate.now().plusDays(ThreadLocalRandom.current().nextInt(30));
                            LocalTime time = LocalTime.of(8 + ThreadLocalRandom.current().nextInt(10), ThreadLocalRandom.current().nextInt(60));
                            
                            ps.setBytes(1, uuidToBytes(slotIds.get(i)));
                            ps.setBytes(2, uuidToBytes(docId));
                            ps.setObject(3, date);
                            ps.setObject(4, time);
                            ps.setObject(5, time.plusMinutes(30));
                        }
                        @Override
                        public int getBatchSize() { return chunkSize; }
                    }
            );

            jdbcTemplate.batchUpdate(
                    "INSERT INTO appointments (id, patient_id, doctor_id, slot_id, appointment_date, start_time, end_time, status, created_at, updated_at) " +
                    "SELECT UUID_TO_BIN(UUID()), ?, doctor_id, id, slot_date, start_time, end_time, 'CONFIRMED', NOW(), NOW() FROM slots WHERE id = ?",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                            UUID patId = patientIds.get(ThreadLocalRandom.current().nextInt(numPatients));
                            ps.setBytes(1, uuidToBytes(patId));
                            ps.setBytes(2, uuidToBytes(slotIds.get(i)));
                        }
                        @Override
                        public int getBatchSize() { return chunkSize; }
                    }
            );
        }

        long end = System.currentTimeMillis();
        log.info("Seeding complete in {}ms", (end - start));
    }
}
