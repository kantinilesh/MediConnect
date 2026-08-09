package com.mediconnect.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediconnect.dto.auth.LoginRequestDto;
import com.mediconnect.dto.auth.RegisterDoctorRequestDto;
import com.mediconnect.dto.auth.RegisterPatientRequestDto;
import com.mediconnect.entity.Doctor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests that confirm cross-role access is correctly rejected.
 *
 * <h3>Test matrix</h3>
 * <pre>
 * | Endpoint                            | No JWT | PATIENT | DOCTOR | ADMIN |
 * |-------------------------------------|--------|---------|--------|-------|
 * | POST /appointments (book)           | 401    | 200     | 403    | 403   |
 * | POST /doctors/{id}/slots/generate   | 401    | 403     | 200    | 403   |
 * | POST /doctors/{id}/availabilities   | 401    | 403     | 200    | 403   |
 * | GET  /appointments/doctor/{id}      | 401    | 403     | 200    | 200   |
 * | GET  /appointments/patient/{id}     | 401    | 200     | 403    | 200   |
 * </pre>
 *
 * <p>Note on 403 vs 400: For POST endpoints that require a body, we send a
 * minimal valid body so that JSON deserialization succeeds. Spring Security's
 * {@code @PreAuthorize} is evaluated after parameter binding but before the
 * method body executes, so if body parsing fails with 400 we won't see the 403.
 * We send the minimum required fields to avoid validation errors.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("RBAC Cross-Role Integration Tests")
class RoleBasedSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Tokens shared across tests (static to persist between @Order'd test methods)
    private static String patientToken;
    private static String doctorToken;
    private static String patientRefreshToken;

    // ── Helper payloads for 403 tests (valid JSON to pass binding before security check) ──

    /** Minimal valid appointment payload — passes @NotNull but refers to a non-existent patient/slot.
     *  With DOCTOR token, we expect 403 before any DB lookup. */
    private static final String APPOINTMENT_PAYLOAD =
        "{\"patientId\":\"00000000-0000-0000-0000-000000000001\","
        + "\"slotId\":\"00000000-0000-0000-0000-000000000002\"}";

    /** Minimal valid availability payload — PATIENT should get 403 before any processing. */
    private static final String AVAILABILITY_PAYLOAD =
        "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"10:00\","
        + "\"slotDurationMinutes\":30}";

    /** Minimal valid slot-generate payload — PATIENT should get 403 before any processing. */
    private static final String SLOT_GENERATE_PAYLOAD =
        "{\"startDate\":\"2099-01-01\",\"endDate\":\"2099-01-07\"}";

    // ── Setup: register & login patient + doctor ──────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Setup: Register and login a PATIENT and a DOCTOR to obtain tokens")
    void setupTokens() throws Exception {
        // Register patient
        RegisterPatientRequestDto patientReq = new RegisterPatientRequestDto();
        patientReq.setFirstName("Alice");
        patientReq.setLastName("TestPatient");
        patientReq.setEmail("rbac.patient.v2@mediconnect.test");
        patientReq.setPassword("AlicePass1!");

        MvcResult patientReg = mockMvc.perform(post("/api/v1/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patientReq)))
                .andExpect(status().isCreated())
                .andReturn();

        patientToken = extractField(patientReg, "accessToken");
        patientRefreshToken = extractField(patientReg, "refreshToken");
        assertThat(patientToken).isNotBlank();

        // Register doctor
        RegisterDoctorRequestDto doctorReq = new RegisterDoctorRequestDto();
        doctorReq.setFirstName("Dr. Bob");
        doctorReq.setLastName("TestDoctor");
        doctorReq.setEmail("rbac.doctor.v2@mediconnect.test");
        doctorReq.setPassword("DoctorPass1!");
        doctorReq.setSpecialization(Doctor.Specialization.CARDIOLOGY);

        MvcResult doctorReg = mockMvc.perform(post("/api/v1/auth/register/doctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doctorReq)))
                .andExpect(status().isCreated())
                .andReturn();

        doctorToken = extractField(doctorReg, "accessToken");
        assertThat(doctorToken).isNotBlank();
    }

    // ── 401 Tests: no JWT at all ──────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("No JWT → POST /appointments → 401 Unauthorized")
    void noJwt_bookAppointment_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPOINTMENT_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("No JWT → POST /doctors/{id}/availabilities → 401 Unauthorized")
    void noJwt_createAvailability_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/doctors/00000000-0000-0000-0000-000000000001/availabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVAILABILITY_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("No JWT → POST /doctors/{id}/slots/generate → 401 Unauthorized")
    void noJwt_generateSlots_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/doctors/00000000-0000-0000-0000-000000000001/slots/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SLOT_GENERATE_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("No JWT → GET /appointments/doctor/{id} → 401 Unauthorized")
    void noJwt_getDoctorAppointments_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/doctor/00000000-0000-0000-0000-000000000001")
                        .param("date", "2025-01-01"))
                .andExpect(status().isUnauthorized());
    }

    // ── 403 Tests: correct auth, wrong role ──────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("PATIENT JWT → POST /doctors/{id}/availabilities (DOCTOR-only) → 403 Forbidden")
    void patient_createAvailability_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/doctors/00000000-0000-0000-0000-000000000001/availabilities")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AVAILABILITY_PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("PATIENT JWT → POST /doctors/{id}/slots/generate (DOCTOR-only) → 403 Forbidden")
    void patient_generateSlots_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/doctors/00000000-0000-0000-0000-000000000001/slots/generate")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SLOT_GENERATE_PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("PATIENT JWT → GET /appointments/doctor/{id} (DOCTOR/ADMIN-only) → 403 Forbidden")
    void patient_getDoctorAppointments_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/doctor/00000000-0000-0000-0000-000000000001")
                        .header("Authorization", "Bearer " + patientToken)
                        .param("date", "2025-01-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("DOCTOR JWT → POST /appointments (PATIENT-only) → 403 Forbidden")
    void doctor_bookAppointment_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(APPOINTMENT_PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("DOCTOR JWT → GET /appointments/patient/{id} (PATIENT/ADMIN-only) → 403 Forbidden")
    void doctor_getPatientAppointments_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/patient/00000000-0000-0000-0000-000000000001")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    // ── Token refresh test ────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Use refresh token from registration to get a new access token")
    void refreshToken_works() throws Exception {
        // Use the refresh token captured during registration (Order 1)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + patientRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    // ── Public endpoints still accessible without JWT ─────────────────────────

    @Test
    @Order(5)
    @DisplayName("No JWT → GET /api/v1/doctors/search (public) → 200 OK")
    void noJwt_searchDoctors_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/search"))
                .andExpect(status().isOk());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractField(MvcResult result, String fieldName) throws Exception {
        String body = result.getResponse().getContentAsString();
        var node = objectMapper.readTree(body);
        return node.path("data").path(fieldName).asText();
    }
}
