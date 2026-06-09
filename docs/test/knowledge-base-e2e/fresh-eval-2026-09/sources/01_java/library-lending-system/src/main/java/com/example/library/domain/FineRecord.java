package com.example.library.domain;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@TableName("fine_records")
public class FineRecord {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField("lending_id") private Long lendingId;
    @TableField("overdue_days") private Integer overdueDays;
    @TableField("fine_amount") private BigDecimal fineAmount;
    @TableField("created_at") private LocalDate createdAt;
    @TableLogic private Integer deleted;
}
