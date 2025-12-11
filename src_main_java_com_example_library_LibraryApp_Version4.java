package com.example.library;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.awt.Desktop;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class LibraryApp extends Application {

    // DB config - update as needed
    private static final String DB_URL = "jdbc:mysql://localhost:3306/library_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "hari";

    private static final String EXCEL_FILE = "transaction_history.xlsx";
    private static final String PDF_FOLDER = "pdf-reports";

    private TableView<Transaction> transactionTableView = new TableView<>();
    private ObservableList<Transaction> transactions = FXCollections.observableArrayList();
    private TableView<Book> bookTableView = new TableView<>();

    private TextField memberIdField = new TextField();
    private TextField memberNameField = new TextField();
    private TextField bookIdField = new TextField();
    private TextField bookTitleField = new TextField();
    private TextField bookPriceField = new TextField();

    private DatePicker borrowDatePicker = new DatePicker(LocalDate.now());
    private DatePicker returnDatePicker = new DatePicker(LocalDate.now().plusDays(14));

    private final DateTimeFormatter pdfDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // ensure pdf folder exists
        try {
            Files.createDirectories(Path.of(PDF_FOLDER));
        } catch (IOException e) {
            // ignore, show alerts when needed
        }

        createTransactionsTableIfNotExists();
        loadTransactionsFromDatabase();
        showLibrarySystem(primaryStage);
    }

    private void showLibrarySystem(Stage primaryStage) {
        memberIdField.setPromptText("Member ID");
        memberNameField.setPromptText("Member Name");
        bookIdField.setPromptText("Book ID");
        bookTitleField.setPromptText("Book Title");
        bookPriceField.setPromptText("Book Price (USD)");

        Button addTransactionButton = new Button("Add Transaction (save DB + Excel)");
        addTransactionButton.setOnAction(e -> addTransaction());

        Button fetchBooksButton = new Button("Fetch Books from Database");
        fetchBooksButton.setOnAction(e -> showBooksFromDatabase());

        Button generatePdfButton = new Button("Generate PDF for Selected");
        generatePdfButton.setOnAction(e -> {
            Transaction selected = transactionTableView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Please select a transaction from the table to generate a PDF.");
                return;
            }
            generatePdfForTransaction(selected, true); // true = open after create
        });

        HBox buttonsRow = new HBox(10, addTransactionButton, fetchBooksButton, generatePdfButton);

        VBox inputBox = new VBox(10,
            new HBox(10, memberIdField, memberNameField),
            new HBox(10, bookIdField, bookTitleField, bookPriceField),
            new HBox(10, borrowDatePicker, returnDatePicker),
            buttonsRow
        );
        inputBox.setPadding(new Insets(10));

        TableColumn<Transaction, String> memberIdCol = new TableColumn<>("Member ID");
        memberIdCol.setCellValueFactory(new PropertyValueFactory<>("memberId"));

        TableColumn<Transaction, String> memberNameCol = new TableColumn<>("Member Name");
        memberNameCol.setCellValueFactory(new PropertyValueFactory<>("memberName"));

        TableColumn<Transaction, String> bookIdCol = new TableColumn<>("Book ID");
        bookIdCol.setCellValueFactory(new PropertyValueFactory<>("bookId"));

        TableColumn<Transaction, String> bookTitleCol = new TableColumn<>("Book Title");
        bookTitleCol.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));

        TableColumn<Transaction, Double> bookPriceCol = new TableColumn<>("Book Price");
        bookPriceCol.setCellValueFactory(new PropertyValueFactory<>("bookPrice"));

        TableColumn<Transaction, String> borrowDateCol = new TableColumn<>("Borrow Date");
        borrowDateCol.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));

        TableColumn<Transaction, String> returnDateCol = new TableColumn<>("Return Date");
        returnDateCol.setCellValueFactory(new PropertyValueFactory<>("returnDate"));

        transactionTableView.getColumns().addAll(memberIdCol, memberNameCol, bookIdCol, bookTitleCol, bookPriceCol, borrowDateCol, returnDateCol);
        transactionTableView.setItems(transactions);
        transactionTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        VBox root = new VBox(10, inputBox, transactionTableView);
        root.setPadding(new Insets(10));

        primaryStage.setTitle("Library Management System");
        primaryStage.setScene(new Scene(root, 980, 600));
        primaryStage.show();
    }

    private void addTransaction() {
        String memberId = memberIdField.getText().trim();
        String memberName = memberNameField.getText().trim();
        String bookId = bookIdField.getText().trim();
        String bookTitle = bookTitleField.getText().trim();
        String borrowDate = borrowDatePicker.getValue().toString();
        String returnDate = returnDatePicker.getValue().toString();
        double bookPrice;

        if (memberId.isEmpty() || memberName.isEmpty() || bookId.isEmpty() || bookTitle.isEmpty() || bookPriceField.getText().trim().isEmpty()) {
            showAlert("Please fill all the fields before adding a transaction.");
            return;
        }

        try {
            bookPrice = Double.parseDouble(bookPriceField.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Book Price must be a valid number!");
            return;
        }

        Transaction transaction = new Transaction(memberId, memberName, bookId, bookTitle, bookPrice, borrowDate, returnDate);

        // Persist to DB
        try {
            saveTransactionToDatabase(transaction);
        } catch (SQLException e) {
            showAlert("Error saving transaction to database: " + e.getMessage());
            return;
        }

        // Add to UI list
        transactions.add(0, transaction); // add at top

        // Save to Excel (keeps Excel copy)
        saveTransactionToExcel(transaction);

        // Clear form
        memberIdField.clear();
        memberNameField.clear();
        bookIdField.clear();
        bookTitleField.clear();
        bookPriceField.clear();
        borrowDatePicker.setValue(LocalDate.now());
        returnDatePicker.setValue(LocalDate.now().plusDays(14));

        showAlert("Transaction added and saved.");
    }

    // DB methods
    private void createTransactionsTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS transactions ("
                + "transaction_id INT AUTO_INCREMENT PRIMARY KEY,"
                + "member_id VARCHAR(100) NOT NULL,"
                + "member_name VARCHAR(255) NOT NULL,"
                + "book_id VARCHAR(100) NOT NULL,"
                + "book_title VARCHAR(255) NOT NULL,"
                + "book_price DOUBLE,"
                + "borrow_date DATE,"
                + "return_date DATE,"
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            showAlert("Error ensuring transactions table exists: " + e.getMessage());
        }
    }

    private void saveTransactionToDatabase(Transaction t) throws SQLException {
        String insert = "INSERT INTO transactions (member_id, member_name, book_id, book_title, book_price, borrow_date, return_date) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, t.getMemberId());
            ps.setString(2, t.getMemberName());
            ps.setString(3, t.getBookId());
            ps.setString(4, t.getBookTitle());
            ps.setDouble(5, t.getBookPrice());
            ps.setDate(6, Date.valueOf(t.getBorrowDate()));
            ps.setDate(7, Date.valueOf(t.getReturnDate()));
            ps.executeUpdate();
        }
    }

    private void loadTransactionsFromDatabase() {
        transactions.clear();
        String q = "SELECT member_id, member_name, book_id, book_title, book_price, borrow_date, return_date FROM transactions ORDER BY created_at DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(q)) {

            while (rs.next()) {
                Transaction t = new Transaction(
                        rs.getString("member_id"),
                        rs.getString("member_name"),
                        rs.getString("book_id"),
                        rs.getString("book_title"),
                        rs.getDouble("book_price"),
                        Optional.ofNullable(rs.getDate("borrow_date")).map(Date::toString).orElse(""),
                        Optional.ofNullable(rs.getDate("return_date")).map(Date::toString).orElse("")
                );
                transactions.add(t);
            }
        } catch (SQLException e) {
            showAlert("Error loading transactions from DB: " + e.getMessage());
        }
    }

    // Excel methods (unchanged except ensuring file exists)
    private void saveTransactionToExcel(Transaction transaction) {
        // Ensure Excel file exists and has header
        File file = new File(EXCEL_FILE);
        if (!file.exists()) {
            initializeExcelFile();
        }

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(EXCEL_FILE))) {
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();

            Row row = sheet.createRow(rowCount + 1);
            row.createCell(0).setCellValue(transaction.getMemberId());
            row.createCell(1).setCellValue(transaction.getMemberName());
            row.createCell(2).setCellValue(transaction.getBookId());
            row.createCell(3).setCellValue(transaction.getBookTitle());
            row.createCell(4).setCellValue(transaction.getBookPrice());
            row.createCell(5).setCellValue(transaction.getBorrowDate());
            row.createCell(6).setCellValue(transaction.getReturnDate());

            try (FileOutputStream fos = new FileOutputStream(EXCEL_FILE)) {
                workbook.write(fos);
            }
        } catch (IOException e) {
            showAlert("Error saving to Excel: " + e.getMessage());
        }
    }

    private void initializeExcelFile() {
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(EXCEL_FILE)) {

            Sheet sheet = workbook.createSheet("Transactions");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Member ID");
            header.createCell(1).setCellValue("Member Name");
            header.createCell(2).setCellValue("Book ID");
            header.createCell(3).setCellValue("Book Title");
            header.createCell(4).setCellValue("Book Price");
            header.createCell(5).setCellValue("Borrow Date");
            header.createCell(6).setCellValue("Return Date");

            workbook.write(fos);
        } catch (IOException e) {
            showAlert("Error initializing Excel file: " + e.getMessage());
        }
    }

    // Books UI
    private void showBooksFromDatabase() {
        Stage booksStage = new Stage();
        booksStage.setTitle("Books from Database");

        // Clear previous columns/items to prevent duplicates when opening multiple times
        bookTableView.getColumns().clear();
        bookTableView.getItems().clear();

        TableColumn<Book, String> idCol = new TableColumn<>("Book ID");
        idCol.setCellValueFactory(param -> param.getValue().bookIdProperty());

        TableColumn<Book, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(param -> param.getValue().titleProperty());

        TableColumn<Book, String> authorCol = new TableColumn<>("Author");
        authorCol.setCellValueFactory(param -> param.getValue().authorProperty());

        bookTableView.getColumns().addAll(idCol, titleCol, authorCol);
        bookTableView.setItems(fetchBooksFromDatabase());

        VBox root = new VBox(10, bookTableView);
        root.setPadding(new Insets(10));

        booksStage.setScene(new Scene(root, 600, 400));
        booksStage.show();
    }

    private ObservableList<Book> fetchBooksFromDatabase() {
        ObservableList<Book> books = FXCollections.observableArrayList();

        try {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM books")) {

                while (rs.next()) {
                    books.add(new Book(
                            rs.getString("book_id"),
                            rs.getString("title"),
                            rs.getString("author")
                    ));
                }

            }
        } catch (SQLException e) {
            showAlert("Error fetching books from database: " + e.getMessage());
        }
        return books;
    }

    // PDF generation with background color, optional logo and improved layout
    private void generatePdfForTransaction(Transaction t, boolean openAfterCreate) {
        String safeMember = t.getMemberId().replaceAll("[^a-zA-Z0-9\\-_.]", "_");
        String safeBook = t.getBookId().replaceAll("[^a-zA-Z0-9\\-_.]", "_");
        String outFileName = PDF_FOLDER + File.separator + "transaction_" + safeMember + "_" + safeBook + ".pdf";

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            // try to load logo from resources
            byte[] logoBytes = null;
            try (InputStream is = LibraryApp.class.getResourceAsStream("/logo.png")) {
                if (is != null) {
                    logoBytes = is.readAllBytes();
                }
            } catch (IOException ignored) {
            }

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float pageW = page.getMediaBox().getWidth();
                float pageH = page.getMediaBox().getHeight();

                // 1) Draw full-page soft background
                cs.setNonStrokingColor(new Color(245, 250, 255)); // very light blue
                cs.addRect(0, 0, pageW, pageH);
                cs.fill();

                // 2) Draw a colored header band
                float margin = 50;
                float headerHeight = 72f;
                cs.setNonStrokingColor(new Color(30, 144, 255)); // dodger blue header
                cs.addRect(0, pageH - headerHeight, pageW, headerHeight);
                cs.fill();

                // Reset to black for text
                cs.setNonStrokingColor(Color.BLACK);

                // Put logo on header if present
                float yStart = pageH - margin;
                if (logoBytes != null) {
                    try {
                        PDImageXObject pdImage = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
                        float imgWidth = 72;
                        float imgHeight = (pdImage.getHeight() * imgWidth) / pdImage.getWidth();
                        cs.drawImage(pdImage, margin, pageH - imgHeight - (headerHeight - imgHeight) / 2, imgWidth, imgHeight);
                    } catch (Exception ex) {
                        // if image fails just continue without logo
                    }
                }

                // Title on header (white color to contrast with blue)
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 20);
                // white text for header area
                cs.setNonStrokingColor(Color.WHITE);
                float titleX = (logoBytes != null) ? margin + 100 : margin;
                cs.newLineAtOffset(titleX, pageH - 40);
                cs.showText("Library Transaction Receipt");
                cs.endText();

                // restore black for content text
                cs.setNonStrokingColor(Color.BLACK);

                // Draw a box for member and book details with subtle white background
                float tableTopY = pageH - headerHeight - 40;
                float tableLeftX = margin;
                float tableWidth = pageW - 2 * margin;
                float rowHeight = 22;
                float col1Width = 160;
                float col2Width = tableWidth - col1Width;

                // white rounded-ish box background (simple rectangle)
                cs.setNonStrokingColor(Color.WHITE);
                cs.addRect(tableLeftX, tableTopY - (rowHeight * 6) - 8, tableWidth, rowHeight * 6 + 16);
                cs.fill();

                // border for the box
                cs.setStrokingColor(Color.LIGHT_GRAY);
                cs.setLineWidth(0.7f);
                cs.addRect(tableLeftX, tableTopY - (rowHeight * 6) - 8, tableWidth, rowHeight * 6 + 16);
                cs.stroke();

                // Draw internal grid lines
                cs.setStrokingColor(Color.LIGHT_GRAY);
                cs.setLineWidth(0.5f);
                for (int i = 0; i <= 6; i++) {
                    float y = tableTopY - i * rowHeight;
                    cs.moveTo(tableLeftX, y);
                    cs.lineTo(tableLeftX + tableWidth, y);
                }
                cs.stroke();

                // Vertical separator
                cs.moveTo(tableLeftX + col1Width, tableTopY);
                cs.lineTo(tableLeftX + col1Width, tableTopY - rowHeight * 6);
                cs.stroke();

                // Fill text into cells
                float textXLeft = tableLeftX + 10;
                float textXRight = tableLeftX + col1Width + 10;
                float textY = tableTopY - 16;

                // Member Details header
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(textXLeft, textY);
                cs.showText("Member Details");
                cs.endText();

                // Member details values
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(textXLeft, textY - rowHeight);
                cs.showText("Member ID: " + t.getMemberId());
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(textXLeft, textY - rowHeight * 2);
                cs.showText("Member Name: " + t.getMemberName());
                cs.endText();

                // Book Details header
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(textXRight, textY);
                cs.showText("Book Details");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(textXRight, textY - rowHeight);
                cs.showText("Book ID: " + t.getBookId());
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(textXRight, textY - rowHeight * 2);
                cs.showText("Title: " + t.getBookTitle());
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(textXRight, textY - rowHeight * 3);
                cs.showText(String.format("Price: $%.2f", t.getBookPrice()));
                cs.endText();

                // Dates (spanning both columns)
                float dateY = textY - rowHeight * 4;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(textXLeft, dateY);
                cs.showText("Transaction Dates");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(textXLeft, dateY - rowHeight);
                cs.showText("Borrow Date: " + t.getBorrowDate());
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(textXLeft, dateY - rowHeight * 2);
                cs.showText("Return Date: " + t.getReturnDate());
                cs.endText();

                // Footer small note
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 11);
                cs.newLineAtOffset(margin, 60);
                cs.showText("Thank you for using the library. Please return on or before the due date.");
                cs.endText();
            }

            // Save PDF
            doc.save(outFileName);
            showAlert("PDF generated successfully: " + outFileName);

            // Open automatically if requested and supported
            if (openAfterCreate && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().open(new File(outFileName));
                } catch (IOException ex) {
                    // ignore; user already informed of file path
                }
            }

        } catch (IOException e) {
            showAlert("Error generating PDF: " + e.getMessage());
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Data classes
    public static class Book {
        private final StringProperty bookId;
        private final StringProperty title;
        private final StringProperty author;

        public Book(String bookId, String title, String author) {
            this.bookId = new SimpleStringProperty(bookId);
            this.title = new SimpleStringProperty(title);
            this.author = new SimpleStringProperty(author);
        }

        public String getBookId() { return bookId.get(); }
        public StringProperty bookIdProperty() { return bookId; }
        public String getTitle() { return title.get(); }
        public StringProperty titleProperty() { return title; }
        public String getAuthor() { return author.get(); }
        public StringProperty authorProperty() { return author; }
    }

    public static class Transaction {
        private final String memberId;
        private final String memberName;
        private final String bookId;
        private final String bookTitle;
        private final double bookPrice;
        private final String borrowDate;
        private final String returnDate;

        public Transaction(String memberId, String memberName, String bookId, String bookTitle, double bookPrice, String borrowDate, String returnDate) {
            this.memberId = memberId;
            this.memberName = memberName;
            this.bookId = bookId;
            this.bookTitle = bookTitle;
            this.bookPrice = bookPrice;
            this.borrowDate = borrowDate;
            this.returnDate = returnDate;
        }

        public String getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public String getBookId() { return bookId; }
        public String getBookTitle() { return bookTitle; }
        public double getBookPrice() { return bookPrice; }
        public String getBorrowDate() { return borrowDate; }
        public String getReturnDate() { return returnDate; }
    }
}