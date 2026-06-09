package com.example.library.dto;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
public class FineCalculationRequest {
    @NotNull private Long lendingId;
    private LocalDate returnDate;
}
