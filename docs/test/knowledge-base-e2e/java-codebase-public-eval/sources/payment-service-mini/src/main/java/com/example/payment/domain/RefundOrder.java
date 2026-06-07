package com.example.payment.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("refund_orders")
public class RefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("refund_id")
    private String refundId;

    @TableField("order_id")
    private String orderId;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("fee_amount")
    private BigDecimal feeAmount;

    @TableField("reason")
    private String reason;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
