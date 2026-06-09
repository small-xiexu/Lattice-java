package com.example.library.service.impl;
import com.example.library.domain.CreditRecord;
import com.example.library.mapper.CreditRecordMapper;
import com.example.library.service.CreditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class CreditServiceImpl implements CreditService {
    private final CreditRecordMapper mapper;
    @Value("${library.credit.deduct-1-7-days}") private int deduct1To7;
    @Value("${library.credit.deduct-8-14-days}") private int deduct8To14;
    @Value("${library.credit.deduct-15-plus-days}") private int deduct15Plus;
    @Value("${library.credit.restore-after-clean-days}") private int restoreDays;
    @Value("${library.credit.restore-amount}") private int restoreAmount;

    public CreditServiceImpl(CreditRecordMapper mapper) { this.mapper = mapper; }

    @Override @Transactional
    public void deductScore(Long userId, int overdueDays) {
        int points;
        if (overdueDays <= 7) { points = deduct1To7; }
        else if (overdueDays <= 14) { points = deduct8To14; }
        else { points = deduct15Plus; }
        mapper.deductCredit(userId, points);
        CreditRecord rec = new CreditRecord();
        rec.setUserId(userId); rec.setChangeAmount(-points); rec.setReason("overdue " + overdueDays + " days");
        rec.setCreatedAt(LocalDate.now()); mapper.insert(rec);
    }

    @Override @Transactional
    public void restoreScore(Long userId) {
        LocalDate cutoff = LocalDate.now().minusDays(restoreDays);
        if (!mapper.hasOverdueSince(userId, cutoff)) {
            mapper.deductCredit(userId, -restoreAmount);
            CreditRecord rec = new CreditRecord();
            rec.setUserId(userId); rec.setChangeAmount(restoreAmount);
            rec.setReason("clean record restore"); rec.setCreatedAt(LocalDate.now());
            mapper.insert(rec);
        }
    }

    @Override public int getCurrentScore(Long userId) { return mapper.getCurrentScore(userId); }
}
