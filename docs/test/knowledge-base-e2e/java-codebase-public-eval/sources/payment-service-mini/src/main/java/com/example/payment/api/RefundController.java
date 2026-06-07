package com.example.payment.api;

import com.example.payment.domain.RefundOrder;
import com.example.payment.dto.RefundRequest;
import com.example.payment.dto.RefundResponse;
import com.example.payment.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public RefundResponse applyRefund(@Valid @RequestBody RefundRequest request) {
        return refundService.processRefund(request);
    }

    @GetMapping("/{refundId}")
    public RefundOrder getRefundStatus(@PathVariable String refundId) {
        return refundService.findByRefundId(refundId);
    }
}
