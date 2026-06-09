package com.example.library.api;
import com.example.library.dto.FineCalculationRequest;
import com.example.library.dto.FineCalculationResponse;
import com.example.library.service.FineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fine")
public class FineController {
    private final FineService fineService;
    public FineController(FineService fineService) { this.fineService = fineService; }

    @PostMapping("/calculate")
    public FineCalculationResponse calculate(@Valid @RequestBody FineCalculationRequest request) {
        return fineService.calculateOverdueFine(request);
    }
}
