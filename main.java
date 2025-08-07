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
                    //System.out.println("You have chosen to browse books.");
                    browseBooks(conn);

                    break;
                case 2:
                    //System.out.println("You have chosen to checkout books.");
                    checkoutBooks(conn, scanner,userId);

                    break;
                case 3:
                    returnBooks(conn, scanner, userId);
                    break;

                case 4:
                    Register(conn, scanner);
                    break;
                
                case 5:
                    System.out.println("Come back soon. Goodbye!");
 
                    break;
                default:
                    System.out.println("That is not a valid option. Please choose an option from the menu.");
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
            scanner.next(); 
            System.out.print(prompt);
        }
        return scanner.nextInt();
    }

    public static void checkoutBooks(Connection conn, Scanner scanner, int userid) {
    while (userid == -1)
        userid = login(conn, scanner);

    ArrayList<Integer> bookIdsToCheckout = new ArrayList<>();
    int bookId;

    System.out.println("\n--- Checkout Books ---");
    System.out.println("Enter the Book ID of each book you want to checkout.");

    do {
        bookId = getUserIntInput(scanner, "Enter a Book ID or 0 to exit: ");

        if (bookId != 0) {
            if (bookExists(conn, bookId)) {
                bookIdsToCheckout.add(bookId);
                System.out.println("Book ID " + bookId + " added to your checkout list.");
            } else {
                System.out.println("Book ID " + bookId + " does not exist. Please try again.");
            }
        }
    } while (bookId != 0);

    if (!bookIdsToCheckout.isEmpty()) {
        System.out.println("Your checkout list: " + bookIdsToCheckout);
        processCheckout(conn, userid, bookIdsToCheckout);
    } else {
        System.out.println("No books were added to the checkout list.");
    }
}


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

                // Add statement to batch for efficient insertion
                pstmtCheckoutItems.addBatch();
            }

            // Execute the batch insert
            int[] updateCounts = pstmtCheckoutItems.executeBatch();
            
            //Check if all items were inserted successfully
            System.out.println("Successfully added " + updateCounts.length + " books to checkout " + generatedCheckoutId);
        }
        //commit if successful
        conn.commit();
        System.out.println("Checkout transaction successful for user " + userId);
        
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
    System.out.print("Please login. \nEnter your name\n");        
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
                    Register(conn, scanner);
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

    String sql = "SELECT book_id FROM checkout_items WHERE user_id = ? AND returned = FALSE;";
    try(PreparedStatement pstmt = conn.prepareStatement(sql)){
        pstmt.setLong(1, userId); // Use the new checkout_id
         try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // If a row is found, display
                    ArrayList<Integer> bookIdsToReturn = new ArrayList<>();
                    int bookId;

                    while (rs.next()) {
                        int id = rs.getInt("book_id");
                        System.out.printf("ID: %d %n", id);
                    }

                        do {
                            bookId = getUserIntInput(scanner, "Enter the Book ID of a book you want to return and enter (0 to finish): ");

                            if (bookId != 0) {
                                bookIdsToReturn.add(bookId);
                                System.out.println("Book ID " + bookId + " added to your return.");
                            }
                        } while (bookId != 0);

                        if (!bookIdsToReturn.isEmpty()) {
                            System.out.println("Your checkout list: " + bookIdsToReturn);
                            processReturn(conn, scanner, userId, bookIdsToReturn);
                        }
   
                }
                else{
                    System.out.println("You have no books checked out to return.");

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
            System.out.println("No books to return.");
            return;
            }

            
            // All changes will be temporary until we call `commit()`.
            try {
            conn.setAutoCommit(false);
            
            String sqlReturn = "UPDATE checkout_items\n" + 
                            "SET returned = TRUE\n" + 
                            "WHERE user_id = ? AND book_id = ?;";
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
            System.out.println("Return transaction successful for user " + userId);
            
        } catch (SQLException e) {
            // In case of error, discard
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

    public static void Register(Connection conn, Scanner scanner){

        try {
            // Prompt user for input
            System.out.println("Register for a library card: ");
            scanner.nextLine();

            System.out.println("Enter name: ");
            String name = scanner.nextLine();

            System.out.print("Enter password: ");
            String password = scanner.nextLine();

            System.out.print("Enter email: ");
            String email = scanner.nextLine();

            System.out.print("Enter street: ");
            String street = scanner.nextLine();

            System.out.print("Enter state (st): ");
            String st = scanner.nextLine();

            System.out.print("Enter zip code: ");
            String zip = scanner.nextLine();

            System.out.print("Enter city: ");
            String city = scanner.nextLine();

            // SQL Insert statement
            String sql = "INSERT INTO user (name, password, email, street, st, zip, city) " +
                         "VALUES ( ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, password);
                pstmt.setString(3, email);
                pstmt.setString(4, street);
                pstmt.setString(5, st);
                pstmt.setString(6, zip);
                pstmt.setString(7, city);

                int rowsInserted = pstmt.executeUpdate();
                if (rowsInserted > 0) {
                    System.out.println("User registered successfully.");
                } else {
                    System.out.println("Failed to register user.");
                }
            }

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. User ID must be an integer.");
        } 
    }
    public static boolean bookExists(Connection conn, int bookId) {
        String sql = "SELECT 1 FROM book WHERE book_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();  // true if book exists
            }
        } catch (SQLException e) {
            System.out.println("Error checking book existence: " + e.getMessage());
            return false;
        }
    }

} //closing library class
