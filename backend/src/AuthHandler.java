
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthHandler {

    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void register(HttpServer server) {
        server.createContext("/api/auth/register", exchange -> {
            if (!method(exchange, "POST")) return;

            try {
                String body = readBody(exchange);
                String name = getString(body, "fullName");
                String email = getString(body, "email")
                        .toLowerCase();
                String phone = getString(body, "phone");
                String password = getString(body, "password");

                if (name.isBlank() || email.isBlank()
                        || password.length() < 8) {
                    send(exchange, 400,
                            "{\"error\":\"Name and email are required; password must be at least 8 characters\"}");
                    return;
                }

                if (!email.matches(
                        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    send(exchange, 400,
                            "{\"error\":\"Enter a valid email address\"}");
                    return;
                }

                if (name.length() > 100 || email.length() > 150
                        || phone.length() > 20) {
                    send(exchange, 400,
                            "{\"error\":\"One or more fields are too long\"}");
                    return;
                }

                String salt = newSalt();
                String hash = hashPassword(password, salt);

                String sql = "INSERT INTO users "
                        + "(full_name, email, phone, password_hash) "
                        + "VALUES (?, ?, ?, ?)";

                try (Connection connection =
                             DatabaseConnection.getConnection();
                     PreparedStatement statement =
                             connection.prepareStatement(sql)) {
                    statement.setString(1, name);
                    statement.setString(2, email);
                    statement.setString(3,
                            phone.isBlank() ? null : phone);
                    statement.setString(4, "PBKDF2$" + salt + "$" + hash);
                    statement.executeUpdate();
                }

                send(exchange, 201,
                        "{\"status\":\"registered\","
                        + "\"message\":\"Account created successfully\"}");

            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    send(exchange, 409,
                            "{\"error\":\"An account with this email already exists\"}");
                } else {
                    System.err.println("Registration database error: "
                            + e.getClass().getSimpleName());
                    send(exchange, 500,
                            "{\"error\":\"Registration failed\"}");
                }
            } catch (IllegalArgumentException e) {
                send(exchange, 400,
                        "{\"error\":\"Invalid registration data\"}");
            } catch (Exception e) {
                System.err.println("Registration error: "
                        + e.getClass().getSimpleName());
                send(exchange, 500,
                        "{\"error\":\"Registration failed\"}");
            }
        });

        server.createContext("/api/auth/login", exchange -> {
            if (!method(exchange, "POST")) return;

            try {
                String body = readBody(exchange);
                String email = getString(body, "email")
                        .toLowerCase();
                String password = getString(body, "password");

                String sql = "SELECT user_id, full_name, password_hash "
                        + "FROM users WHERE email = ?";

                try (Connection connection =
                             DatabaseConnection.getConnection();
                     PreparedStatement statement =
                             connection.prepareStatement(sql)) {
                    statement.setString(1, email);

                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next() || !verifyPassword(password,
                                rs.getString("password_hash"))) {
                            send(exchange, 401,
                                    "{\"error\":\"Invalid email or password\"}");
                            return;
                        }

                        long userId = rs.getLong("user_id");
                        String fullName = rs.getString("full_name");
                        String token = UUID.randomUUID().toString()
                                + UUID.randomUUID().toString()
                                .replace("-", "");
                        try (PreparedStatement session = connection.prepareStatement(
                                "INSERT INTO user_sessions (session_token, user_id, expires_at) "
                                + "VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '7 days')")) {
                            session.setString(1, token);
                            session.setLong(2, userId);
                            session.executeUpdate();
                        }

                        exchange.getResponseHeaders().add(
                                "Set-Cookie",
                                "CineReserveSession=" + token
                                + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800");

                        send(exchange, 200,
                                "{\"status\":\"ok\","
                                + "\"userId\":" + userId + ","
                                + "\"fullName\":\"" + jsonEscape(fullName)
                                + "\"}");
                    }
                }
            } catch (Exception e) {
                System.err.println("Login error: "
                        + e.getClass().getSimpleName());
                send(exchange, 500,
                        "{\"error\":\"Login failed\"}");
            }
        });

        server.createContext("/api/auth/me", exchange -> {
            if (!method(exchange, "GET")) return;

            Long userId = authenticatedUser(exchange);
            if (userId == null) {
                send(exchange, 401,
                        "{\"error\":\"Login required\"}");
                return;
            }

            String sql = "SELECT user_id, full_name, email "
                    + "FROM users WHERE user_id = ?";

            try (Connection connection =
                         DatabaseConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {
                statement.setLong(1, userId);

                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        send(exchange, 401,
                                "{\"error\":\"Session is invalid\"}");
                        return;
                    }

                    send(exchange, 200,
                            "{\"userId\":" + rs.getLong("user_id")
                            + ",\"fullName\":\""
                            + jsonEscape(rs.getString("full_name"))
                            + "\",\"email\":\""
                            + jsonEscape(rs.getString("email")) + "\"}");
                }
            } catch (SQLException e) {
                send(exchange, 500,
                        "{\"error\":\"Could not verify session\"}");
            }
        });

        server.createContext("/api/auth/logout", exchange -> {
            if (!method(exchange, "POST")) return;

            String token = getCookie(exchange, "CineReserveSession");
            if (token != null) {
                try (Connection connection = DatabaseConnection.getConnection();
                     PreparedStatement statement = connection.prepareStatement(
                             "DELETE FROM user_sessions WHERE session_token = ?")) {
                    statement.setString(1, token);
                    statement.executeUpdate();
                } catch (SQLException e) {
                    System.err.println("Logout session cleanup failed.");
                }
            }

            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    "CineReserveSession=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");

            send(exchange, 200, "{\"status\":\"logged_out\"}");
        });
    }

    public static Long authenticatedUser(HttpExchange exchange) {
        String token = getCookie(exchange, "CineReserveSession");
        if (token == null) return null;
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT user_id FROM user_sessions WHERE session_token = ? AND expires_at > CURRENT_TIMESTAMP")) {
            statement.setString(1, token);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }


private static boolean method(
        HttpExchange exchange, String expected) throws IOException {

    if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
        addCors(exchange);

        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return false;
    }

    if (expected.equalsIgnoreCase(exchange.getRequestMethod())) {
        return true;
    }

    send(exchange, 405,
            "{\"error\":\"Method not allowed\"}");
    return false;
    }


    private static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String hashPassword(
            String password, String salt) throws Exception {
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        KeySpec spec = new PBEKeySpec(
                password.toCharArray(), saltBytes,
                ITERATIONS, KEY_LENGTH);

        byte[] hash = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();

        return Base64.getEncoder().encodeToString(hash);
    }

    private static boolean verifyPassword(
            String password, String stored) throws Exception {
        if (stored == null || !stored.startsWith("PBKDF2$")) {
            return false;
        }

        String[] parts = stored.split("\\$", 3);
        if (parts.length != 3) return false;

        byte[] actual = Base64.getDecoder().decode(
                hashPassword(password, parts[1]));
        byte[] expected = Base64.getDecoder().decode(parts[2]);

        return java.security.MessageDigest.isEqual(actual, expected);
    }

    private static String getCookie(
            HttpExchange exchange, String name) {
        String cookieHeader =
                exchange.getRequestHeaders().getFirst("Cookie");

        if (cookieHeader == null) return null;

        for (String cookie : cookieHeader.split(";")) {
            String[] pair = cookie.trim().split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return pair[1];
            }
        }

        return null;
    }

    private static String readBody(HttpExchange exchange)
            throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private static String getString(String body, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) return "";

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static void send(
            HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store");
        addCors(exchange);

        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void addCors(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.equals("null")
                || origin.matches("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?"))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}
