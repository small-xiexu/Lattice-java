package com.example.library.api;
import com.example.library.domain.CreditRecord;
import com.example.library.service.CreditService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credit")
public class CreditController {
    private final CreditService creditService;
    public CreditController(CreditService creditService) { this.creditService = creditService; }

    @GetMapping("/{userId}")
    public Integer getCreditScore(@PathVariable Long userId) {
        return creditService.getCurrentScore(userId);
    }
}
