package com.example.payment.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class PaymentRequest {

    @NotBlank
    private String merchantId;

    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("50000.00")
    private BigDecimal amount;

    @NotBlank
    private String currency;

    @NotBlank
    private String channel;

    @NotBlank
    private String idempotencyKey;
}
