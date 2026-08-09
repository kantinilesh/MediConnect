package com.mediconnect.service.impl;

import com.mediconnect.dto.auth.*;
import com.mediconnect.entity.*;
import com.mediconnect.exception.ResourceNotFoundException;
import com.mediconnect.repository.*;
import com.mediconnect.security.JwtTokenProvider;
import com.mediconnect.security.UserDetailsServiceImpl;
import com.mediconnect.service.AuthService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Implements user registration, login, and JWT token management.
 *
 * <ul>
 *   <li>BCrypt password hashing on registration.</li>
 *   <li>Spring Security {@link AuthenticationManager} for credential verification.</li>
 *   <li>DB-backed refresh tokens for explicit revocation (one active token per user).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository         userRepository;
    private final PatientRepository      patientRepository;
    private final DoctorRepository       doctorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final AuthenticationManager  authenticationManager;
    private final JwtTokenProvider       jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final EntityManager          entityManager;

    // ── Registration ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponseDto registerPatient(RegisterPatientRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        Patient patient = Patient.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.PATIENT)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .enabled(true)
                .build();

        patient = patientRepository.save(patient);
        log.info("Patient registered: {}", patient.getEmail());
        return issueTokens(patient);
    }

    @Override
    @Transactional
    public AuthResponseDto registerDoctor(RegisterDoctorRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        Doctor doctor = Doctor.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.DOCTOR)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .specialization(request.getSpecialization())
                .qualifications(request.getQualifications())
                .clinicName(request.getClinicName())
                .clinicAddress(request.getClinicAddress())
                .yearsOfExperience(request.getYearsOfExperience())
                .consultationFee(request.getConsultationFee())
                .bio(request.getBio())
                .enabled(true)
                .build();

        doctor = doctorRepository.save(doctor);
        log.info("Doctor registered: {}", doctor.getEmail());
        return issueTokens(doctor);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponseDto login(LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));

        String refreshTokenValue = rotateRefreshToken(user);

        log.info("User logged in: {}", user.getEmail());

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponseDto refreshToken(TokenRefreshRequestDto request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Refresh token not found or already revoked"));

        if (storedToken.getRevoked()) {
            throw new IllegalArgumentException("Refresh token has been revoked. Please log in again.");
        }
        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new IllegalArgumentException("Refresh token has expired. Please log in again.");
        }

        User user = storedToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtTokenProvider.generateAccessTokenFromUserDetails(userDetails);

        // Rotate: issue new refresh token
        String newRefreshTokenValue = rotateRefreshToken(user);

        log.info("Tokens refreshed for user: {}", user.getEmail());

        return AuthResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenValue)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Issues tokens for a newly registered user (no password re-verify needed).
     */
    private AuthResponseDto issueTokens(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtTokenProvider.generateAccessTokenFromUserDetails(userDetails);
        String refreshTokenValue = rotateRefreshToken(user);

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Creates a new refresh token for the user, revoking any existing one (one-per-user policy).
     *
     * @return the new raw refresh token string to return to the client
     */
    private String rotateRefreshToken(User user) {
        // Delete any existing refresh token for this user.
        // We DELETE (not just revoke) because RefreshToken has @OneToOne on user_id;
        // leaving the old record would violate the unique constraint when inserting the new one.
        int deleted = refreshTokenRepository.deleteByUser(user);
        if (deleted > 0) {
            // Flush the DELETE to the DB before the INSERT so the constraint isn't violated
            entityManager.flush();
        }

        String tokenValue = jwtTokenProvider.generateRefreshTokenValue();
        Instant expiry = Instant.now().plusMillis(
                7 * 24 * 60 * 60 * 1000L
        );

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiryDate(expiry)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }
}
