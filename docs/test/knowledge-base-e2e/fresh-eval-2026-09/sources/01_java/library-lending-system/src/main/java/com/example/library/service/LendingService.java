package com.example.library.service;
import com.example.library.dto.LendingRequest;
import com.example.library.dto.LendingResponse;

public interface LendingService {
    LendingResponse borrow(LendingRequest request);
    LendingResponse returnBook(Long lendingId);
    int getBorrowedCount(Long userId);
}
