package com.example.library.api;
import com.example.library.dto.LendingRequest;
import com.example.library.dto.LendingResponse;
import com.example.library.service.LendingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lending")
public class LendingController {
    private final LendingService lendingService;
    public LendingController(LendingService lendingService) { this.lendingService = lendingService; }

    @PostMapping("/borrow")
    public LendingResponse borrow(@Valid @RequestBody LendingRequest request) {
        return lendingService.borrow(request);
    }

    @PostMapping("/return")
    public LendingResponse returnBook(@RequestParam Long lendingId) {
        return lendingService.returnBook(lendingId);
    }
}
