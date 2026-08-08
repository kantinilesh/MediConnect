package com.mediconnect.service;

import com.mediconnect.dto.doctor.DoctorResponseDto;
import com.mediconnect.dto.doctor.DoctorSearchCriteria;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface DoctorService {

    Page<DoctorResponseDto> searchDoctors(DoctorSearchCriteria criteria);

    DoctorResponseDto getDoctorById(UUID doctorId);
}
