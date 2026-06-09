-- Migration 002: Add credit_score column to users table
ALTER TABLE users ADD COLUMN credit_score INT DEFAULT 100;

-- Backfill existing users
UPDATE users SET credit_score = 100 WHERE credit_score IS NULL;
