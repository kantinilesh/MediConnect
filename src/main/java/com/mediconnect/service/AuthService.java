package com.mediconnect.service;

import com.mediconnect.dto.auth.*;

public interface AuthService {

    AuthResponseDto registerPatient(RegisterPatientRequestDto request);

    AuthResponseDto registerDoctor(RegisterDoctorRequestDto request);

    AuthResponseDto login(LoginRequestDto request);

    AuthResponseDto refreshToken(TokenRefreshRequestDto request);
}
