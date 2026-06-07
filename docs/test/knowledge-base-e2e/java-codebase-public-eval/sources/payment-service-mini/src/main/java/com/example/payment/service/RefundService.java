package com.example.payment.service;

import com.example.payment.domain.RefundOrder;
import com.example.payment.dto.RefundRequest;
import com.example.payment.dto.RefundResponse;

public interface RefundService {

    RefundResponse processRefund(RefundRequest request);

    RefundOrder findByRefundId(String refundId);
}
