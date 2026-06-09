package com.example.library.service;
public interface CreditService {
    void deductScore(Long userId, int overdueDays);
    void restoreScore(Long userId);
    int getCurrentScore(Long userId);
}
