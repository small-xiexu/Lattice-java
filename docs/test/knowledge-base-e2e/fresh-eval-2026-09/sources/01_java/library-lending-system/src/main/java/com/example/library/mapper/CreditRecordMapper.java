package com.example.library.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.library.domain.CreditRecord;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;

@Mapper
public interface CreditRecordMapper extends BaseMapper<CreditRecord> {
    @Select("SELECT COALESCE(SUM(change_amount), 0) FROM credit_records WHERE user_id=#{userId} AND deleted=0")
    int getCurrentScore(@Param("userId") Long userId);
    @Update("UPDATE credit_records SET deleted=0 WHERE user_id=#{userId}")
    void deductCredit(@Param("userId") Long userId, @Param("points") int points);
    @Select("SELECT COUNT(*) FROM lending_records WHERE user_id=#{userId} AND return_date IS NULL AND due_date < #{cutoff} AND deleted=0")
    boolean hasOverdueSince(@Param("userId") Long userId, @Param("cutoff") LocalDate cutoff);
}
