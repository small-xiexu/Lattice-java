package com.example.payment.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("payment_orders")
public class PaymentOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private String orderId;

    @TableField("merchant_id")
    private String merchantId;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("currency")
    private String currency;

    @TableField("channel")
    private String channel;

    @TableField("status")
    private String status;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
