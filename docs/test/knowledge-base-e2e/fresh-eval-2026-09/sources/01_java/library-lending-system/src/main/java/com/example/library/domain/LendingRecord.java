package com.example.library.domain;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
@TableName("lending_records")
public class LendingRecord {
    @TableId(type=IdType.AUTO) private Long id;
    @TableField("user_id") private Long userId;
    @TableField("book_id") private Long bookId;
    @TableField("borrow_date") private LocalDate borrowDate;
    @TableField("due_date") private LocalDate dueDate;
    @TableField("return_date") private LocalDate returnDate;
    @TableLogic private Integer deleted;
}
