package com.example.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class RefundRequest {

    @NotBlank
    private String orderId;

    @NotNull
    @jakarta.validation.constraints.DecimalMin("0.01")
    private BigDecimal refundAmount;

    @NotBlank
    private String reason;

    @NotBlank
    private String idempotencyKey;
}
