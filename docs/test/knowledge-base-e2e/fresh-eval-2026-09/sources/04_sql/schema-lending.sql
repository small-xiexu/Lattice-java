CREATE TABLE lending_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    borrow_date DATE NOT NULL,
    due_date DATE NOT NULL,
    return_date DATE,
    deleted INT DEFAULT 0
);

CREATE TABLE credit_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    change_amount INT NOT NULL,
    reason VARCHAR(255),
    created_at DATE NOT NULL,
    deleted INT DEFAULT 0
);

CREATE TABLE fine_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lending_id BIGINT NOT NULL,
    overdue_days INT NOT NULL,
    fine_amount DECIMAL(10,2) NOT NULL,
    created_at DATE NOT NULL,
    deleted INT DEFAULT 0
);
