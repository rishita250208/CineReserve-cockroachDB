import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL =
            System.getenv().getOrDefault(
                    "CINERESERVE_DB_URL",
                    "jdbc:postgresql://localhost:26257/cinereserve?sslmode=disable"
            );

    private static final String USERNAME =
            System.getenv().getOrDefault(
                    "CINERESERVE_DB_USER",
                    "root"
            );

    private static final String PASSWORD =
            System.getenv().getOrDefault("CINERESERVE_DB_PASSWORD", "");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    public static void main(String[] args) {
        try (Connection connection = getConnection()) {
            System.out.println("CockroachDB connected successfully!");
            System.out.println("Connection valid: "
                    + connection.isValid(5));
        } catch (SQLException e) {
            System.out.println("Database connection failed.");
            System.out.println(e.getMessage());
        }
    }
}
