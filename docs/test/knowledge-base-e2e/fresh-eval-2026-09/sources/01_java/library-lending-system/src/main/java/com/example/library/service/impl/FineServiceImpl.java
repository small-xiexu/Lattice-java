package com.example.library.service.impl;
import com.example.library.domain.FineRecord;
import com.example.library.dto.FineCalculationRequest;
import com.example.library.dto.FineCalculationResponse;
import com.example.library.mapper.FineRecordMapper;
import com.example.library.mapper.LendingRecordMapper;
import com.example.library.service.FineService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class FineServiceImpl implements FineService {
    private final LendingRecordMapper lendingMapper;
    private final FineRecordMapper fineMapper;
    @Value("${library.fine.rate-1-7}") private BigDecimal rate1To7;
    @Value("${library.fine.rate-8-14}") private BigDecimal rate8To14;
    @Value("${library.fine.rate-15-plus}") private BigDecimal rate15Plus;
    @Value("${library.fine.max-days}") private int maxDays;

    public FineServiceImpl(LendingRecordMapper lm, FineRecordMapper fm) { this.lendingMapper = lm; this.fineMapper = fm; }

    @Override @Transactional
    public FineCalculationResponse calculateOverdueFine(FineCalculationRequest request) {
        var lending = lendingMapper.selectById(request.getLendingId());
        LocalDate returnDate = request.getReturnDate() != null ? request.getReturnDate() : LocalDate.now();
        long days = ChronoUnit.DAYS.between(lending.getDueDate(), returnDate);
        if (days <= 0) { FineCalculationResponse r = new FineCalculationResponse(); r.setFineAmount(BigDecimal.ZERO); return r; }
        if (days > maxDays) days = maxDays;
        BigDecimal rate = days <= 7 ? rate1To7 : days <= 14 ? rate8To14 : rate15Plus;
        BigDecimal amount = rate.multiply(BigDecimal.valueOf(days));
        FineRecord rec = new FineRecord();
        rec.setLendingId(request.getLendingId()); rec.setOverdueDays((int) days);
        rec.setFineAmount(amount); rec.setCreatedAt(LocalDate.now());
        fineMapper.insert(rec);
        FineCalculationResponse resp = new FineCalculationResponse();
        resp.setLendingId(request.getLendingId()); resp.setOverdueDays((int) days); resp.setFineAmount(amount);
        return resp;
    }
}
