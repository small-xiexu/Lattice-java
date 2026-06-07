package com.example.payment.service.impl;

import com.example.payment.domain.PaymentOrder;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.mapper.PaymentOrderMapper;
import com.example.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderMapper mapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${payment.max-order-amount}")
    private BigDecimal maxOrderAmount;

    @Value("${payment.idempotency-key-ttl-seconds}")
    private long idempotencyTtl;

    public PaymentServiceImpl(PaymentOrderMapper mapper,
                              RedisTemplate<String, String> redisTemplate) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        if (request.getAmount().compareTo(maxOrderAmount) > 0) {
            throw new IllegalArgumentException(
                "amount exceeds max: " + maxOrderAmount);
        }

        String idempotencyKey = "idem:pay:" + request.getIdempotencyKey();
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(idempotencyKey, "1", Duration.ofSeconds(idempotencyTtl));
        if (Boolean.FALSE.equals(locked)) {
            PaymentOrder existing = mapper.selectByOrderId(
                redisTemplate.opsForValue().get(idempotencyKey));
            return toResponse(existing);
        }

        PaymentOrder order = new PaymentOrder();
        order.setOrderId(UUID.randomUUID().toString());
        order.setMerchantId(request.getMerchantId());
        order.setAmount(request.getAmount());
        order.setCurrency(request.getCurrency());
        order.setChannel(request.getChannel());
        order.setStatus("CREATED");
        order.setIdempotencyKey(request.getIdempotencyKey());
        order.setCreatedAt(LocalDateTime.now());

        mapper.insert(order);

        redisTemplate.opsForValue().set(idempotencyKey,
            order.getOrderId(), Duration.ofSeconds(idempotencyTtl));

        return toResponse(order);
    }

    @Override
    public PaymentOrder findByOrderId(String orderId) {
        return mapper.selectByOrderId(orderId);
    }

    private PaymentResponse toResponse(PaymentOrder order) {
        PaymentResponse resp = new PaymentResponse();
        resp.setOrderId(order.getOrderId());
        resp.setStatus(order.getStatus());
        resp.setAmount(order.getAmount());
        resp.setCurrency(order.getCurrency());
        resp.setChannel(order.getChannel());
        resp.setCreatedAt(order.getCreatedAt());
        return resp;
    }
}
