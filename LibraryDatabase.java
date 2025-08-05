// LibraryApp.java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class LibraryDatabase {

    // Database connection details
    private static final String DB_URL = "jdbc:mysql://localhost:3306/mayaslibrary";
    private static final String USER = "root"; // MySQL username
    private static final String PASS = "password"; // MySQL password

    public static void main(String[] args) {
        // Attempt to establish a connection to the database
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            System.out.println("Successfully connected to the database!");

            // --- Example 1: Retrieving all books ---
            System.out.println("\n--- All Books in the Library ---");
            String sqlSelectAll = "SELECT book_id, title, author FROM book";
            ResultSet rs = stmt.executeQuery(sqlSelectAll);

            // Iterate through the result set and print book details
            while (rs.next()) {
                // Retrieve by column name or column index
                int id = rs.getInt("book_id");
                String title = rs.getString("title");
                String author = rs.getString("author");

                System.out.printf("ID: %d, Title: %s, Author: %s%n",
                                  id, title, author);
            }
            rs.close(); // Close the ResultSet

            // --- Example 2: Adding a new book (INSERT) ---
            System.out.println("\n--- Adding a New Book ---");
            String newTitle = "The Hitchhiker''s Guide to the Galaxy";
            String newAuthor = "Douglas Adams";
         


            String sqlInsert = String.format(
                "INSERT INTO book (title, author) VALUES ('%s', '%s')",
                newTitle, newAuthor
            );
            int rowsAffected = stmt.executeUpdate(sqlInsert);
            if (rowsAffected > 0) {
                System.out.println("New book added successfully!");
            } else {
                System.out.println("Failed to add new book.");
            }

            // --- Example 3: Retrieving books after insertion ---
            System.out.println("\n--- Books After Insertion ---");
            rs = stmt.executeQuery(sqlSelectAll); // Re-execute to see the new book
            while (rs.next()) {
                System.out.printf(" Title: %s, Author: %s%n",
                                  rs.getInt("book_id"), rs.getString("title"),
                                  rs.getString("author"));
            }
            rs.close();

        } catch (SQLException e) {
            // Handle any SQL exceptions (e.g., connection failed, query error)
            System.err.println("Database error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Handle other exceptions
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
