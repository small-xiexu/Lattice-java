package com.example.payment.service.impl;

import com.example.payment.domain.PaymentOrder;
import com.example.payment.domain.RefundOrder;
import com.example.payment.dto.RefundRequest;
import com.example.payment.dto.RefundResponse;
import com.example.payment.mapper.PaymentOrderMapper;
import com.example.payment.mapper.RefundOrderMapper;
import com.example.payment.service.RefundService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefundServiceImpl implements RefundService {

    private final PaymentOrderMapper paymentMapper;
    private final RefundOrderMapper refundMapper;

    @Value("${payment.refund-window-minutes}")
    private int refundWindowMinutes;

    @Value("${payment.partial-refund-window-minutes}")
    private int partialRefundWindowMinutes;

    @Value("${payment.partial-refund-fee-percent}")
    private int partialRefundFeePercent;

    public RefundServiceImpl(PaymentOrderMapper paymentMapper,
                             RefundOrderMapper refundMapper) {
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
    }

    @Override
    @Transactional
    public RefundResponse processRefund(RefundRequest request) {
        PaymentOrder order = paymentMapper.selectByOrderId(request.getOrderId());
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }

        long minutesSinceCreate = Duration.between(
            order.getCreatedAt(), LocalDateTime.now()).toMinutes();

        BigDecimal fee = BigDecimal.ZERO;
        String status;

        if (minutesSinceCreate <= refundWindowMinutes) {
            status = "FULL_REFUND";
        } else if (minutesSinceCreate <= partialRefundWindowMinutes) {
            status = "PARTIAL_REFUND";
            fee = request.getRefundAmount()
                .multiply(BigDecimal.valueOf(partialRefundFeePercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            throw new IllegalArgumentException("refund window exceeded");
        }

        RefundOrder refund = new RefundOrder();
        refund.setRefundId(UUID.randomUUID().toString());
        refund.setOrderId(request.getOrderId());
        refund.setRefundAmount(request.getRefundAmount());
        refund.setFeeAmount(fee);
        refund.setReason(request.getReason());
        refund.setStatus(status);
        refund.setCreatedAt(LocalDateTime.now());

        refundMapper.insert(refund);

        RefundResponse resp = new RefundResponse();
        resp.setRefundId(refund.getRefundId());
        resp.setOrderId(refund.getOrderId());
        resp.setRefundAmount(refund.getRefundAmount());
        resp.setFeeAmount(refund.getFeeAmount());
        resp.setStatus(refund.getStatus());
        resp.setCreatedAt(refund.getCreatedAt());
        return resp;
    }

    @Override
    public RefundOrder findByRefundId(String refundId) {
        return refundMapper.selectByRefundId(refundId);
    }
}
