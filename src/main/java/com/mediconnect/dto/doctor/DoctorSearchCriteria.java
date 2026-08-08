package com.mediconnect.dto.doctor;

import com.mediconnect.entity.Doctor.Specialization;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Search and filter criteria for Doctor Discovery API.
 */
@Data
public class DoctorSearchCriteria {

    private Specialization specialization;
    private String name;
    private String location;
    private BigDecimal minRating;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate availableDate;

    private int page = 0;
    private int size = 10;
    private String sortBy = "rating";
    private String sortDirection = "desc";
}
