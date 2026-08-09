package com.mediconnect.dto.auth;

import com.mediconnect.entity.Doctor.Specialization;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Registration request DTO for new DOCTOR accounts.
 */
@Data
public class RegisterDoctorRequestDto {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @Size(max = 20)
    private String phone;

    @NotNull(message = "Specialization is required")
    private Specialization specialization;

    private String qualifications;

    @Size(max = 255)
    private String clinicName;

    @Size(max = 512)
    private String clinicAddress;

    private Integer yearsOfExperience;

    private BigDecimal consultationFee;

    private String bio;
}
