package com.example.library.service;
import com.example.library.dto.FineCalculationRequest;
import com.example.library.dto.FineCalculationResponse;

public interface FineService {
    FineCalculationResponse calculateOverdueFine(FineCalculationRequest request);
}
