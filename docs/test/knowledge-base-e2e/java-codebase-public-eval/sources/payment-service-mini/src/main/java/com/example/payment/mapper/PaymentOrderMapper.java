package com.example.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payment.domain.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    PaymentOrder selectByOrderId(@Param("orderId") String orderId);

    int countByMerchantAndStatus(@Param("merchantId") String merchantId,
                                  @Param("status") String status);
}
