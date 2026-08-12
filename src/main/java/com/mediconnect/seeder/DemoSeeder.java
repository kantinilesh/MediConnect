package com.mediconnect.seeder;

import com.mediconnect.dto.auth.RegisterDoctorRequestDto;
import com.mediconnect.dto.auth.RegisterPatientRequestDto;
import com.mediconnect.dto.slot.SlotGenerateRequestDto;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.service.AuthService;
import com.mediconnect.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final SlotService slotService;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            log.info("Database is not empty ({} users). Skipping DemoSeeder.", userRepository.count());
            return;
        }

        log.info("Database is empty. Running DemoSeeder...");

        // 1. Create Patients
        RegisterPatientRequestDto patient1 = new RegisterPatientRequestDto();
        patient1.setEmail("patient1@example.com");
        patient1.setPassword("password123");
        patient1.setFirstName("John");
        patient1.setLastName("Doe");
        patient1.setPhoneNumber("+1234567890");
        authService.registerPatient(patient1);

        RegisterPatientRequestDto patient2 = new RegisterPatientRequestDto();
        patient2.setEmail("patient2@example.com");
        patient2.setPassword("password123");
        patient2.setFirstName("Jane");
        patient2.setLastName("Smith");
        patient2.setPhoneNumber("+1987654321");
        authService.registerPatient(patient2);

        // 2. Create Doctors
        String[][] doctorData = {
                {"Alice", "Johnson", "Cardiology", "10 years experience, specializes in heart disease."},
                {"Bob", "Williams", "Dermatology", "Expert in skin care and cosmetic dermatology."},
                {"Charlie", "Brown", "Neurology", "Specializes in migraines and nerve disorders."},
                {"Diana", "Prince", "Pediatrics", "Board-certified pediatrician for children of all ages."},
                {"Evan", "Wright", "Orthopedics", "Surgeon specializing in sports injuries."}
        };

        for (int i = 0; i < doctorData.length; i++) {
            RegisterDoctorRequestDto doctor = new RegisterDoctorRequestDto();
            doctor.setEmail("doctor" + (i + 1) + "@example.com");
            doctor.setPassword("password123");
            doctor.setFirstName(doctorData[i][0]);
            doctor.setLastName(doctorData[i][1]);
            doctor.setSpecialization(doctorData[i][2]);
            doctor.setBio(doctorData[i][3]);

            var response = authService.registerDoctor(doctor);
            UUID doctorId = response.getUser().getId();

            // 3. Generate Slots for each doctor for the next 7 days
            try {
                LocalDate today = LocalDate.now();
                slotService.generateSlots(doctorId, today, today.plusDays(6));
                log.info("Generated slots for Dr. {}", doctorData[i][0]);
            } catch (Exception e) {
                log.warn("Could not generate slots for Dr. {}: {}", doctorData[i][0], e.getMessage());
            }
        }

        log.info("DemoSeeder finished successfully.");
    }
}
