-- Create database and sample books table
CREATE DATABASE IF NOT EXISTS library_db;
USE library_db;

CREATE TABLE IF NOT EXISTS books (
  book_id VARCHAR(50) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(255),
  price DOUBLE
);

-- Sample data
INSERT INTO books (book_id, title, author, price) VALUES
('B001', 'Effective Java', 'Joshua Bloch', 45.0),
('B002', 'Clean Code', 'Robert C. Martin', 40.0),
('B003', 'Java Concurrency in Practice', 'Brian Goetz', 50.0)
ON DUPLICATE KEY UPDATE title=VALUES(title);

-- Transactions table for persisting borrow records
CREATE TABLE IF NOT EXISTS transactions (
  transaction_id INT AUTO_INCREMENT PRIMARY KEY,
  member_id VARCHAR(100) NOT NULL,
  member_name VARCHAR(255) NOT NULL,
  book_id VARCHAR(100) NOT NULL,
  book_title VARCHAR(255) NOT NULL,
  book_price DOUBLE,
  borrow_date DATE,
  return_date DATE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);