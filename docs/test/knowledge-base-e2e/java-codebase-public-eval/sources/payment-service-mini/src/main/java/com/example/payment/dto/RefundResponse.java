package com.example.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RefundResponse {

    private String refundId;
    private String orderId;
    private BigDecimal refundAmount;
    private BigDecimal feeAmount;
    private String status;
    private LocalDateTime createdAt;
}
