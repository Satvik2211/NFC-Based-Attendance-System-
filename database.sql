CREATE DATABASE attendance_db;

USE attendance_db;

CREATE TABLE students (
    roll_number VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100),
    branch VARCHAR(50),
    year INT
);

CREATE TABLE attendance (
    id INT AUTO_INCREMENT PRIMARY KEY,
    roll_number VARCHAR(30),
    scan_time DATETIME,
    scan_date DATE,
    FOREIGN KEY (roll_number) REFERENCES students(roll_number)
);
