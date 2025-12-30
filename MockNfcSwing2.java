import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;
import java.util.Vector;

public class MockNfcSwing2 extends JFrame {

    // -------- JDBC CONFIG (change if needed) --------
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/attendance_db?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";          // your MySQL user
    private static final String DB_PASS = "Satvik@2005";   // your MySQL password

    // -------- UI COMPONENTS --------
    private JLabel statusLabel;      // shows "Place your ID card..." / "ROLL: ..."
    private JTextField inputField;   // encrypted input
    private JTextArea logArea;       // log output

    // last successfully decrypted roll (for student report)
    private String lastDecryptedRoll = null;

    public MockNfcSwing2() {
        setTitle("NFC Attendance (Encrypted Demo)");
        setSize(700, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // ===== TOP AREA: status label + Admin login at top-right =====
        statusLabel = new JLabel("Place your ID card on the reader...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 22));

        JButton adminButton = new JButton("Admin Login");

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(statusLabel, BorderLayout.CENTER);

        JPanel adminBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        adminBtnPanel.add(adminButton);
        topPanel.add(adminBtnPanel, BorderLayout.EAST);

        // ===== CENTER: log area =====
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // ===== BOTTOM: encrypted input + Student Report + Simulate Scan =====
        JPanel bottomPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();

        JButton studentReportBtn = new JButton("Student Report");
        JButton scanButton       = new JButton("Simulate Scan");

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightButtons.add(studentReportBtn);
        rightButtons.add(scanButton);

        bottomPanel.add(new JLabel("Encrypted roll: "), BorderLayout.WEST);
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(rightButtons, BorderLayout.EAST);

        // ===== MAIN LAYOUT =====
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Button actions
        scanButton.addActionListener((ActionEvent e) -> handleScan());
        adminButton.addActionListener((ActionEvent e) -> {
            AdminLoginDialog dlg = new AdminLoginDialog(this);
            dlg.setVisible(true);
        });
        studentReportBtn.addActionListener((ActionEvent e) -> handleStudentReport());
    }

    // -------- BUTTON HANDLER (scan) --------
    private void handleScan() {
        String encryptedRoll = inputField.getText().trim();
        if (encryptedRoll.isEmpty()) {
            appendLog("No encrypted roll entered.");
            return;
        }

        // Step 1: decrypt (rule: $ -> A)
        String decryptedRoll = decryptRoll(encryptedRoll);
        lastDecryptedRoll = decryptedRoll; // store for student report
        statusLabel.setText("ROLL: " + decryptedRoll);

        // Step 2: DB verify + mark attendance
        try {
            if (!studentExists(decryptedRoll)) {
                appendLog("❌ Student not found in database: " + decryptedRoll);
            } else {
                markAttendance(decryptedRoll);
                appendLog("✅ Attendance marked for: " + decryptedRoll);
            }
        } catch (Exception ex) {
            appendLog("Error while accessing DB: " + ex.getMessage());
            ex.printStackTrace();
        }

        inputField.setText("");
    }

    // -------- STUDENT REPORT HANDLER --------
    private void handleStudentReport() {
        if (lastDecryptedRoll == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "No student scanned yet.\nPlease scan a card first.",
                    "Student Report",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        try {
            DefaultTableModel model = loadAttendanceForStudent(lastDecryptedRoll);
            StudentReportDialog dlg = new StudentReportDialog(this, lastDecryptedRoll, model);
            dlg.setVisible(true);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load student attendance: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();
        }
    }

    // -------- DECRYPTION LOGIC --------
    // For now: replace '$' with 'A'
    private String decryptRoll(String encrypted) {
        return encrypted.replace('$', 'A');
    }

    // -------- JDBC HELPERS --------

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // Check if roll exists in students table
    private boolean studentExists(String roll) throws SQLException {
        String sql = "SELECT 1 FROM students WHERE roll_number = ?";

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, roll);
            try (ResultSet rs = pst.executeQuery()) {
                return rs.next(); // true if at least one row
            }
        }
    }

    // Insert attendance record
    private void markAttendance(String roll) throws SQLException {
        String sql = "INSERT INTO attendance (roll_number, scan_time, scan_date) " +
                     "VALUES (?, NOW(), CURDATE())";

        try (Connection con = getConnection();
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, roll);
            pst.executeUpdate();
        }
    }

    // Load attendance table model for admin report (all students)
    static DefaultTableModel loadAttendanceTableModel() throws SQLException {
        String[] cols = {"ID", "Roll", "Name", "Branch", "Year", "Scan Time", "Scan Date"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        String sql =
                "SELECT a.id, a.roll_number, s.name, s.branch, s.year, a.scan_time, a.scan_date " +
                "FROM attendance a " +
                "LEFT JOIN students s ON a.roll_number = s.roll_number " +
                "ORDER BY a.scan_time DESC";

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pst = con.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt("id"));
                row.add(rs.getString("roll_number"));
                row.add(rs.getString("name"));
                row.add(rs.getString("branch"));
                row.add(rs.getInt("year"));
                row.add(rs.getTimestamp("scan_time").toString());
                row.add(rs.getDate("scan_date").toString());
                model.addRow(row);
            }
        }
        return model;
    }

    // Load attendance for a single student (used in student report)
    static DefaultTableModel loadAttendanceForStudent(String roll) throws SQLException {
        String[] cols = {"ID", "Roll", "Scan Time", "Scan Date"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);

        String sql =
                "SELECT id, roll_number, scan_time, scan_date " +
                "FROM attendance " +
                "WHERE roll_number = ? " +
                "ORDER BY scan_time DESC";

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, roll);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("roll_number"));
                    row.add(rs.getTimestamp("scan_time").toString());
                    row.add(rs.getDate("scan_date").toString());
                    model.addRow(row);
                }
            }
        }
        return model;
    }

    // Add student to DB
    static void addStudentToDb(String roll, String name, String branch, int year) throws SQLException {
        String sql = "INSERT INTO students (roll_number, name, branch, year) VALUES (?, ?, ?, ?)";

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, roll);
            pst.setString(2, name);
            pst.setString(3, branch);
            pst.setInt(4, year);
            pst.executeUpdate();
        }
    }

    // Remove student from DB
    static void removeStudentFromDb(String roll) throws SQLException {
        String sql = "DELETE FROM students WHERE roll_number = ?";

        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, roll);
            pst.executeUpdate();
        }
    }

    // -------- LOG HELPER --------
    private void appendLog(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ================== ADMIN LOGIN DIALOG ==================
    static class AdminLoginDialog extends JDialog {
        private JTextField userField;
        private JPasswordField passField;
        private JLabel errorLabel;

        public AdminLoginDialog(JFrame parent) {
            super(parent, "Admin Login", true);
            setSize(320, 180);
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout());

            JPanel fields = new JPanel(new GridLayout(2, 2, 5, 5));
            fields.add(new JLabel("Username:"));
            userField = new JTextField();
            fields.add(userField);
            fields.add(new JLabel("Password:"));
            passField = new JPasswordField();
            fields.add(passField);

            errorLabel = new JLabel(" ", SwingConstants.CENTER);
            errorLabel.setForeground(Color.RED);

            JButton loginBtn = new JButton("Login");
            loginBtn.addActionListener(e -> doLogin());

            add(errorLabel, BorderLayout.NORTH);
            add(fields, BorderLayout.CENTER);
            add(loginBtn, BorderLayout.SOUTH);
        }

        private void doLogin() {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());

            // Credentials:
            // username: admin@satvik
            // password: admin
            if (u.equals("admin@satvik") && p.equals("admin")) {
                dispose();
                SwingUtilities.invokeLater(AdminPanel::new);
            } else {
                errorLabel.setText("Invalid username or password");
            }
        }
    }

    // ================== ADMIN PANEL FRAME ==================
    static class AdminPanel extends JFrame {

        private JTable attendanceTable;
        private JTextField rollField, nameField, branchField, yearField;

        public AdminPanel() {
            setTitle("Admin Panel");
            setSize(850, 500);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            JTabbedPane tabs = new JTabbedPane();

            // ---- TAB 1: Attendance Report ----
            JPanel reportPanel = new JPanel(new BorderLayout());
            attendanceTable = new JTable();
            JButton refreshBtn = new JButton("Refresh");

            refreshBtn.addActionListener(e -> loadAttendance());

            reportPanel.add(new JScrollPane(attendanceTable), BorderLayout.CENTER);
            reportPanel.add(refreshBtn, BorderLayout.SOUTH);

            tabs.addTab("Attendance Report", reportPanel);

            // ---- TAB 2: Add / Remove Student ----
            JPanel studentPanel = new JPanel(new BorderLayout());
            JPanel form = new JPanel(new GridLayout(4, 2, 5, 5));
            rollField = new JTextField();
            nameField = new JTextField();
            branchField = new JTextField();
            yearField = new JTextField();

            form.add(new JLabel("Roll Number:"));
            form.add(rollField);
            form.add(new JLabel("Name:"));
            form.add(nameField);
            form.add(new JLabel("Branch:"));
            form.add(branchField);
            form.add(new JLabel("Year:"));
            form.add(yearField);

            JButton addBtn = new JButton("Add Student");
            JButton removeBtn = new JButton("Remove Student");

            addBtn.addActionListener(e -> addStudentAction());
            removeBtn.addActionListener(e -> removeStudentAction());

            JPanel btnPanel = new JPanel();
            btnPanel.add(addBtn);
            btnPanel.add(removeBtn);

            studentPanel.add(form, BorderLayout.CENTER);
            studentPanel.add(btnPanel, BorderLayout.SOUTH);

            tabs.addTab("Add / Remove Student", studentPanel);

            add(tabs);
            setVisible(true);

            loadAttendance(); // initial load
        }

        private void loadAttendance() {
            try {
                DefaultTableModel model = MockNfcSwing2.loadAttendanceTableModel();
                attendanceTable.setModel(model);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to load attendance: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        private void addStudentAction() {
            String roll = rollField.getText().trim();
            String name = nameField.getText().trim();
            String branch = branchField.getText().trim();
            String yearText = yearField.getText().trim();

            if (roll.isEmpty() || name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Roll and Name are required.");
                return;
            }

            int year = 0;
            if (!yearText.isEmpty()) {
                try {
                    year = Integer.parseInt(yearText);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            "Year must be a number.");
                    return;
                }
            }

            try {
                MockNfcSwing2.addStudentToDb(roll, name, branch, year);
                JOptionPane.showMessageDialog(this,
                        "Student added successfully.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to add student: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        private void removeStudentAction() {
            String roll = rollField.getText().trim();
            if (roll.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Enter a roll number to remove.");
                return;
            }

            try {
                MockNfcSwing2.removeStudentFromDb(roll);
                JOptionPane.showMessageDialog(this,
                        "Student removed (if existed).");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to remove student: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    // ================== STUDENT REPORT DIALOG ==================
    static class StudentReportDialog extends JDialog {
        public StudentReportDialog(JFrame parent, String roll, DefaultTableModel model) {
            super(parent, "Attendance for " + roll, true);
            setSize(600, 400);
            setLocationRelativeTo(parent);

            JTable table = new JTable(model);
            add(new JScrollPane(table), BorderLayout.CENTER);
        }
    }

    // -------- MAIN --------
    public static void main(String[] args) {
        try {
            // Load MySQL driver (optional on newer Java, but safe)
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC driver not found. Add connector JAR to classpath.");
        }

        SwingUtilities.invokeLater(() -> {
            MockNfcSwing2 ui = new MockNfcSwing2();
            ui.setVisible(true);
        });
    }
}
