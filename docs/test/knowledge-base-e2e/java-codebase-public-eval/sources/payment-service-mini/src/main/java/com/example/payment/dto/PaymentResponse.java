package com.example.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

    private String orderId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String channel;
    private LocalDateTime createdAt;
}
