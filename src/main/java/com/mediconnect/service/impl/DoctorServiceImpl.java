package com.mediconnect.service.impl;

import com.mediconnect.dto.doctor.DoctorResponseDto;
import com.mediconnect.dto.doctor.DoctorSearchCriteria;
import com.mediconnect.entity.Doctor;
import com.mediconnect.exception.ResourceNotFoundException;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;

    @Override
    public Page<DoctorResponseDto> searchDoctors(DoctorSearchCriteria criteria) {
        Sort.Direction direction = "desc".equalsIgnoreCase(criteria.getSortDirection())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, criteria.getSortBy() != null ? criteria.getSortBy() : "rating");

        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);

        Page<Doctor> page = doctorRepository.searchDoctors(
                criteria.getSpecialization(),
                criteria.getName(),
                criteria.getLocation(),
                criteria.getMinRating(),
                criteria.getAvailableDate(),
                pageable
        );

        return page.map(DoctorResponseDto::fromEntity);
    }

    @Override
    public DoctorResponseDto getDoctorById(UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", "id", doctorId));
        return DoctorResponseDto.fromEntity(doctor);
    }
}
