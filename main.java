import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;
import java.util.ArrayList; 
import java.sql.PreparedStatement;
import java.sql.Timestamp;

class HelloWorld {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/mayaslibrary";
    private static final String USER = "root"; // Your MySQL username
    private static final String PASS = "password"; // Your MySQL password

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            
            System.out.println("Successfully connected to the database!");   
        System.out.println("Welcome to Mayas library!\n"); 

        int choice = -1;        
        int userId = -1;     
        Scanner scanner = new Scanner(System.in);

        while(choice != 5){
            System.out.print("\n1. Browse books\n2. Checkout books\n3. Return books\n4. Register for a library card\n5. Leave library\n"); 
            
            choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    System.out.println("You have chosen to browse books.");
                    browseBooks(conn);

                    break;
                case 2:
                    System.out.println("You have chosen to checkout books.");
                    checkoutBooks(conn, scanner,userId);

                    break;
                case 3:
                    System.out.println("You have chosen to return books.");
                    returnBooks(conn, scanner, userId);
                    break;
                case 4:
                    System.out.println("You have chosen to register for a library card.");
                    // For example: you might call a method like registerCard();
                    break;
                
                case 5:
                    break;
                default:
                    // This block is executed if the user's input doesn't match any of the cases
                    System.out.println("That is not a valid option. Please choose a number from 1 to 4.");
                    break;
            }

            

        } //closing library menu
        scanner.close();
        } catch (SQLException e) {
            // Handle any SQL exceptions that occur during connection or method calls
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();
        }
    } //closing main

    public static void browseBooks(Connection conn) {
        try (Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT book_id, title, author FROM book")) {

        System.out.println("\n--- All Books in the Library ---");

        // Check if the result set is empty
        if (!rs.isBeforeFirst()) {
            System.out.println("No books found in the library.");
            return;
        }

        while (rs.next()) {
            int id = rs.getInt("book_id");
            String title = rs.getString("title");
            String author = rs.getString("author");
            System.out.printf("ID: %d, Title: %s, Author: %s%n", id, title, author);
        }

        } catch (SQLException e) {
            System.err.println("Error Browse books: " + e.getMessage());
            e.printStackTrace();
        } //close catch

    } //close browse books

     private static int getUserIntInput(Scanner scanner, String prompt) {
        System.out.print(prompt);
        while (!scanner.hasNextInt()) {
            System.out.println("Invalid input. Please enter a whole number.");
            scanner.next(); // Consume the invalid input
            System.out.print(prompt);
        }
        return scanner.nextInt();
    }

    public static void checkoutBooks(Connection conn, Scanner scanner, int userid) {
        
        while(userid==-1)
            userid = login(conn, scanner);
        
        ArrayList<Integer> bookIdsToCheckout = new ArrayList<>();
        int bookId;

        System.out.println("\n--- Checkout Books ---");
        System.out.println("Enter the Book ID of each book you want to checkout.");

        do {
            bookId = getUserIntInput(scanner, "Enter a Book ID or 0 to exit: ");

            if (bookId != 0) {
                bookIdsToCheckout.add(bookId);
                System.out.println("Book ID " + bookId + " added to your checkout list.");
            }
        } while (bookId != 0);

        if (!bookIdsToCheckout.isEmpty()) {
            System.out.println("Your checkout list: " + bookIdsToCheckout);
            processCheckout(conn, userid, bookIdsToCheckout);
        } else {
            System.out.println("No books were added to the checkout list.");
        }
    } //close checkout books

    public static void processCheckout(Connection conn, int userId, ArrayList<Integer> bookIdsToCheckout){

        if (bookIdsToCheckout == null || bookIdsToCheckout.isEmpty()) {
        System.out.println("No books to checkout.");
        return;
        }

        String sqlCheckout = "INSERT INTO checkout (user_id, timeAndDate, qty) VALUES (?, ?, ?)";
        String sqlCheckoutItems = "INSERT INTO checkout_items (checkout_id, book_id, user_id) VALUES (?, ?,?)";

        // All changes will be temporary until we call `commit()`.
        try {
        conn.setAutoCommit(false);
        long generatedCheckoutId = -1;
        try (PreparedStatement pstmtCheckout = conn.prepareStatement(sqlCheckout, Statement.RETURN_GENERATED_KEYS)) {
            
            // Set the parameters for the checkout transaction
            pstmtCheckout.setInt(1, userId);
            pstmtCheckout.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            pstmtCheckout.setInt(3, bookIdsToCheckout.size());


            // Execute the insert
            int affectedRows = pstmtCheckout.executeUpdate();

            // Check if the insert was successful and get the new ID
            if (affectedRows == 0) {
                throw new SQLException("Creating checkout failed, no rows affected.");
            }

            // Retrieve the auto-generated checkout_id
            try (ResultSet generatedKeys = pstmtCheckout.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    generatedCheckoutId = generatedKeys.getLong(1);
                } else {
                    throw new SQLException("Creating checkout failed, no ID obtained.");
                }
            }
        }

        try (PreparedStatement pstmtCheckoutItems = conn.prepareStatement(sqlCheckoutItems)) {
            
            // Loop through the list of book IDs
            for (int bookId : bookIdsToCheckout) {
                // Set the parameters for each book item
                pstmtCheckoutItems.setLong(1, generatedCheckoutId); // Use the new checkout_id
                pstmtCheckoutItems.setInt(2, bookId);
                pstmtCheckoutItems.setInt(3, userId);

                // Add this statement to a batch for efficient insertion
                pstmtCheckoutItems.addBatch();
            }

            // Execute the batch insert
            int[] updateCounts = pstmtCheckoutItems.executeBatch();
            
            //Check if all items were inserted successfully
            System.out.println("Successfully added " + updateCounts.length + " books to checkout " + generatedCheckoutId);
        }

        // If all statements above ran without an exception, commit the changes to the database.
        conn.commit();
        System.out.println("Checkout transaction successfully committed for user " + userId);
        
    } catch (SQLException e) {
        // If failure, roll back
        try {
            if (conn != null) {
                System.err.println("Transaction is being rolled back due to an error.");
                conn.rollback();
            }
        } catch (SQLException ex) {
            System.err.println("Failed to rollback transaction: " + ex.getMessage());
            ex.printStackTrace();
        }
        
        System.err.println("Checkout failed: " + e.getMessage());
        e.printStackTrace();
        
    } finally {
        // Always reset auto-commit back to true in the finally block
        try {
            if (conn != null) {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Failed to reset auto-commit: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
public static int login(Connection conn, Scanner scanner){
    int userId = 0;
    System.out.print("Enter your name\n");        
    var name = scanner.next();
     String sql = "SELECT user_id FROM user WHERE name = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {

                    // If a row is found, get the user_id
                    userId = rs.getInt("user_id");
                    System.out.println("Login successful for user: " + name +"\nuserid: "+ userId);
                } else {
                    // No matching name found
                    System.out.println("Login failed: User '" + name + "' not found.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during login: " + e.getMessage());
            e.printStackTrace();
        }
        return userId;
}

public static void returnBooks(Connection conn, Scanner scanner, int userId){
     while(userId==-1)
            userId = login(conn, scanner);

    System.out.println("The books you have checked out are:");

    //search for book id checked out with this user id

    String sql = "SELECT book_id FROM checkout_items WHERE user_id = ?";
    try(PreparedStatement pstmt = conn.prepareStatement(sql)){
        pstmt.setLong(1, userId); // Use the new checkout_id
         try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // If a row is found, display
                    while (rs.next()) {
                        int id = rs.getInt("book_id");
                        System.out.printf("ID: %d %n", id);

                        ArrayList<Integer> bookIdsToReturn = new ArrayList<>();
                        int bookId;
                        System.out.println("Enter the Book ID of each book you want to return:");

                        do {
                            bookId = getUserIntInput(scanner, "Enter a Book ID or 0 to exit: ");

                            if (bookId != 0) {
                                bookIdsToReturn.add(bookId);
                                System.out.println("Book ID " + bookId + " added to your checkout list.");
                            }
                        } while (bookId != 0);

                        if (!bookIdsToReturn.isEmpty()) {
                            System.out.println("Your checkout list: " + bookIdsToReturn);
                            processReturn(conn, scanner, userId, bookIdsToReturn);
                        } else {
                            // No matching name found
                            System.out.println("You have no books checked out to return.");
                        }
                    }
                }
            } catch (SQLException e) {
            System.err.println("Database error during login: " + e.getMessage());
            e.printStackTrace();
            }
    } catch (SQLException e) {
        System.out.println("return books exception in");
    }

}

public static void processReturn(Connection conn, Scanner scanner, int userId, ArrayList<Integer> bookIdsToReturn){
     if (bookIdsToReturn == null || bookIdsToReturn.isEmpty()) {
        System.out.println("No books to checkout.");
        return;
        }

        
        // All changes will be temporary until we call `commit()`.
        try {
        conn.setAutoCommit(false);
        
        String sqlReturn = "UPDATE checkout_items\n" + 
                        "SET returned = TRUE\n" + 
                        "WHERE user_id = ? AND book_id = ?; VALUES (?, ?)";
        try (PreparedStatement pstmtCheckoutItems = conn.prepareStatement(sqlReturn)) {
            
            // Loop through the list of book IDs
            for (int bookId : bookIdsToReturn) {
                // Set the parameters for each book item                
                pstmtCheckoutItems.setInt(1, userId);
                pstmtCheckoutItems.setInt(2, bookId);

                // Add this statement to a batch for efficient insertion
                pstmtCheckoutItems.addBatch();
            }

            int[] updateCounts = pstmtCheckoutItems.executeBatch();
            
            //Check if all items were inserted successfully
            System.out.println("Successfully returned " + updateCounts.length + " books");
        }

        // If all statements above ran without an exception, commit the changes to the database.
        conn.commit();
        System.out.println("Return transaction successfully committed for user " + userId);
        
    } catch (SQLException e) {
        // If failure, roll back
        try {
            if (conn != null) {
                System.err.println("Transaction is being rolled back due to an error.");
                conn.rollback();
            }
        } catch (SQLException ex) {
            System.err.println("Failed to rollback transaction: " + ex.getMessage());
            ex.printStackTrace();
        }
        
        System.err.println("Checkout failed: " + e.getMessage());
        e.printStackTrace();
        
    } finally {
        // Always reset auto-commit back to true in the finally block
        try {
            if (conn != null) {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Failed to reset auto-commit: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

} //closing library class
