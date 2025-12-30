# Secure Attendance Management System

## Description
A Java Swing based desktop application integrated with MySQL using JDBC to manage student attendance.
The system verifies encrypted identifiers, marks attendance in real time, and provides admin-controlled
student management and attendance reporting features.

## Technologies Used
- Java (Swing)
- JDBC
- MySQL
- SQL

## Features
- Encrypted identifier verification
- Attendance marking with database integration
- Admin authentication
- Add / remove student records
- Attendance reporting
- Controlled student attendance view

## Database Schema
The project uses two main tables:
- students
- attendance

Schema details are provided in `database.sql`.

## How to Run
1. Clone the repository
2. Create the database using `database.sql`
3. Update database credentials in the Java file
4. Compile and run the application

## Future Enhancements
- Stronger encryption mechanisms
- Duplicate attendance prevention
- Role-based access control
