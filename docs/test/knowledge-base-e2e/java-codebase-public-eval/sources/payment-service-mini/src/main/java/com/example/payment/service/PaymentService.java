package com.example.payment.service;

import com.example.payment.domain.PaymentOrder;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;

public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request);

    PaymentOrder findByOrderId(String orderId);
}
