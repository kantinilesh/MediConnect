package com.mediconnect.dto.doctor;

import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Doctor.Specialization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for Doctor profile details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorResponseDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private Specialization specialization;
    private String qualifications;
    private String clinicName;
    private String clinicAddress;
    private Integer yearsOfExperience;
    private BigDecimal consultationFee;
    private String bio;
    private BigDecimal rating;

    public static DoctorResponseDto fromEntity(Doctor doctor) {
        return DoctorResponseDto.builder()
                .id(doctor.getId())
                .email(doctor.getEmail())
                .firstName(doctor.getFirstName())
                .lastName(doctor.getLastName())
                .phone(doctor.getPhone())
                .specialization(doctor.getSpecialization())
                .qualifications(doctor.getQualifications())
                .clinicName(doctor.getClinicName())
                .clinicAddress(doctor.getClinicAddress())
                .yearsOfExperience(doctor.getYearsOfExperience())
                .consultationFee(doctor.getConsultationFee())
                .bio(doctor.getBio())
                .rating(doctor.getRating())
                .build();
    }
}
