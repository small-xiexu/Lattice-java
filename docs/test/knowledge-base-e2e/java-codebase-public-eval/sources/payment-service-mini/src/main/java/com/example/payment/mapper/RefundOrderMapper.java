package com.example.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payment.domain.RefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    RefundOrder selectByRefundId(@Param("refundId") String refundId);
}
