package com.example.library.domain;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
@TableName("credit_records")
public class CreditRecord {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField("user_id") private Long userId;
    @TableField("change_amount") private Integer changeAmount;
    @TableField("reason") private String reason;
    @TableField("created_at") private LocalDate createdAt;
    @TableLogic private Integer deleted;
}
