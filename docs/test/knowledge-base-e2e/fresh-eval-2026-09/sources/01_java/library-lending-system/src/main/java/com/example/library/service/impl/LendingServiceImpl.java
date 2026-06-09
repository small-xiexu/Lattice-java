package com.example.library.service.impl;
import com.example.library.domain.LendingRecord;
import com.example.library.dto.LendingRequest;
import com.example.library.dto.LendingResponse;
import com.example.library.mapper.LendingRecordMapper;
import com.example.library.service.LendingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class LendingServiceImpl implements LendingService {
    private final LendingRecordMapper mapper;
    @Value("${library.lending.max-books-per-user}") private int maxBooksPerUser;

    public LendingServiceImpl(LendingRecordMapper mapper) { this.mapper = mapper; }

    @Override @Transactional
    public LendingResponse borrow(LendingRequest request) {
        int current = mapper.countActiveByUserId(request.getUserId());
        if (current + request.getBookIds().size() > maxBooksPerUser) {
            throw new IllegalArgumentException("exceeds max books per user: " + maxBooksPerUser);
        }
        for (Long bookId : request.getBookIds()) {
            LendingRecord rec = new LendingRecord();
            rec.setUserId(request.getUserId()); rec.setBookId(bookId);
            rec.setBorrowDate(LocalDate.now());
            rec.setDueDate(LocalDate.now().plusDays(30));
            mapper.insert(rec);
        }
        LendingResponse resp = new LendingResponse();
        resp.setUserId(request.getUserId()); resp.setBorrowedCount(current + request.getBookIds().size());
        return resp;
    }

    @Override @Transactional
    public LendingResponse returnBook(Long lendingId) {
        mapper.updateReturnDate(lendingId, LocalDate.now());
        LendingResponse resp = new LendingResponse(); resp.setLendingId(lendingId); resp.setReturned(true);
        return resp;
    }

    @Override public int getBorrowedCount(Long userId) { return mapper.countActiveByUserId(userId); }
}
