package com.example.library.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.library.domain.LendingRecord;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;

@Mapper
public interface LendingRecordMapper extends BaseMapper<LendingRecord> {
    @Select("SELECT COUNT(*) FROM lending_records WHERE user_id=#{userId} AND return_date IS NULL AND deleted=0")
    int countActiveByUserId(@Param("userId") Long userId);
    @Update("UPDATE lending_records SET return_date=#{returnDate} WHERE id=#{id}")
    void updateReturnDate(@Param("id") Long id, @Param("returnDate") LocalDate returnDate);
}
