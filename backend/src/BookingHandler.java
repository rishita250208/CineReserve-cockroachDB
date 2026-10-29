
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BookingHandler {

    public static void register(HttpServer server) {

        // Existing booking API
        server.createContext("/api/bookings", exchange -> {
            if ("OPTIONS".equalsIgnoreCase(
                    exchange.getRequestMethod())) {
                send(exchange, 204, "");
                return;
            }

            switch (exchange.getRequestMethod().toUpperCase()) {
                case "POST": handleBooking(exchange); return;
                case "GET": handleGetBookings(exchange); return;
                case "DELETE": handleCancelBooking(exchange); return;
                default:
                    send(exchange, 405,
                            "{\"error\":\"GET, POST, or DELETE method required\"}");
            }
        });

        // New API: Get seats for a particular show
        server.createContext("/api/seats", exchange -> {
            if ("OPTIONS".equalsIgnoreCase(
                    exchange.getRequestMethod())) {
                send(exchange, 204, "");
                return;
            }

            if (!"GET".equalsIgnoreCase(
                    exchange.getRequestMethod())) {
                send(exchange, 405,
                        "{\"error\":\"GET method required\"}");
                return;
            }

            try {
                String query =
                        exchange.getRequestURI().getQuery();

                long showId = -1;

                if (query != null) {
                    for (String parameter : query.split("&")) {
                        String[] parts = parameter.split("=", 2);

                        if (parts.length == 2
                                && "showId".equals(parts[0])) {
                            showId = Long.parseLong(parts[1]);
                            break;
                        }
                    }
                }

                if (showId <= 0) {
                    send(exchange, 400,
                            "{\"error\":\"Valid showId required\"}");
                    return;
                }

                String sql =
                    "SELECT ss.show_seat_id, s.seat_id, " +
                    "s.seat_row, s.seat_number, s.seat_type, " +
                    "ms.base_price AS price, ss.seat_status " +
                    "FROM show_seats ss " +
                    "JOIN seats s ON s.seat_id = ss.seat_id " +
                    "JOIN movie_shows ms ON ms.show_id = ss.show_id " +
                    "WHERE ss.show_id = ? " +
                    "ORDER BY s.seat_row, s.seat_number";

                try (Connection connection =
                             DatabaseConnection.getConnection();
                     PreparedStatement statement =
                             connection.prepareStatement(sql)) {

                    statement.setLong(1, showId);

                    try (ResultSet rs = statement.executeQuery()) {
                        StringBuilder json = new StringBuilder("[");
                        boolean first = true;

                        while (rs.next()) {
                            if (!first) {
                                json.append(",");
                            }

                            first = false;

                            json.append("{")
                                .append("\"showSeatId\":")
                                .append(rs.getLong("show_seat_id"))
                                .append(",\"seatId\":")
                                .append(rs.getLong("seat_id"))
                                .append(",\"row\":\"")
                                .append(rs.getString("seat_row"))
                                .append("\",\"number\":")
                                .append(rs.getInt("seat_number"))
                                .append(",\"type\":\"")
                                .append(rs.getString("seat_type"))
                                .append("\",\"price\":")
                                .append(rs.getBigDecimal("price"))
                                .append(",\"status\":\"")
                                .append(rs.getString("seat_status"))
                                .append("\"}");
                        }

                        json.append("]");
                        send(exchange, 200, json.toString());
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    send(exchange, 500,
                            "{\"error\":\"Could not load seats\"}");
                }

            } catch (NumberFormatException e) {
                send(exchange, 400,
                        "{\"error\":\"Invalid showId\"}");
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                send(exchange, 500,
                        "{\"error\":\"Unexpected server error\"}");
            }
        });
    }

    private static void handleGetBookings(HttpExchange exchange) throws IOException {
        Long userId = AuthHandler.authenticatedUser(exchange);
        if (userId == null) {
            send(exchange, 401, "{\"error\":\"Login required\"}");
            return;
        }
        String sql = "SELECT b.booking_id, b.booking_status, b.booking_time, "
                + "b.total_amount, m.title AS movie_title, th.theatre_name, "
                + "th.city, sc.screen_name, ms.show_time, "
                + "COUNT(t.ticket_id) AS ticket_count "
                + "FROM bookings b JOIN movie_shows ms ON ms.show_id=b.show_id "
                + "JOIN movies m ON m.movie_id=ms.movie_id "
                + "JOIN screens sc ON sc.screen_id=ms.screen_id "
                + "JOIN theatres th ON th.theatre_id=sc.theatre_id "
                + "LEFT JOIN tickets t ON t.booking_id=b.booking_id "
                + "WHERE b.user_id=? GROUP BY b.booking_id, m.title, "
                + "th.theatre_name, th.city, sc.screen_name, ms.show_time "
                + "ORDER BY b.booking_time DESC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement s = c.prepareStatement(sql)) {
            s.setLong(1, userId);
            try (ResultSet rs = s.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"bookingId\":").append(rs.getLong("booking_id"))
                        .append(",\"status\":\"").append(json(rs.getString("booking_status")))
                        .append("\",\"bookingTime\":\"").append(json(rs.getString("booking_time")))
                        .append("\",\"movieTitle\":\"").append(json(rs.getString("movie_title")))
                        .append("\",\"theatre\":\"").append(json(rs.getString("theatre_name")))
                        .append("\",\"city\":\"").append(json(rs.getString("city")))
                        .append("\",\"screen\":\"").append(json(rs.getString("screen_name")))
                        .append("\",\"showTime\":\"").append(json(rs.getString("show_time")))
                        .append("\",\"ticketCount\":").append(rs.getInt("ticket_count"))
                        .append(",\"amount\":").append(rs.getBigDecimal("total_amount"))
                        .append('}');
                }
                send(exchange, 200, json.append(']').toString());
            }
        } catch (SQLException e) {
            System.err.println("Could not load bookings: " + e.getSQLState());
            send(exchange, 500, "{\"error\":\"Could not load bookings\"}");
        }
    }

    private static void handleCancelBooking(HttpExchange exchange) throws IOException {
        Long userId = AuthHandler.authenticatedUser(exchange);
        if (userId == null) {
            send(exchange, 401, "{\"error\":\"Login required\"}");
            return;
        }
        long bookingId;
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.matches("(?:^|&)bookingId=\\d+(?:&|$)"))
                throw new IllegalArgumentException();
            bookingId = Long.parseLong(query.replaceAll(".*bookingId=(\\d+).*", "$1"));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "{\"error\":\"Valid bookingId required\"}");
            return;
        }
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT booking_status FROM bookings WHERE booking_id=? AND user_id=? FOR UPDATE")) {
                    s.setLong(1, bookingId); s.setLong(2, userId);
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next()) { c.rollback(); send(exchange, 404, "{\"error\":\"Booking not found\"}"); return; }
                        if (!"CONFIRMED".equals(rs.getString(1))) { c.rollback(); send(exchange, 409, "{\"error\":\"Only confirmed bookings can be cancelled\"}"); return; }
                    }
                }
                try (PreparedStatement s = c.prepareStatement("UPDATE bookings SET booking_status='CANCELLED' WHERE booking_id=?")) { s.setLong(1, bookingId); s.executeUpdate(); }
                try (PreparedStatement s = c.prepareStatement("UPDATE tickets SET ticket_status='CANCELLED' WHERE booking_id=?")) { s.setLong(1, bookingId); s.executeUpdate(); }
                try (PreparedStatement s = c.prepareStatement("UPDATE show_seats SET seat_status='AVAILABLE' WHERE show_seat_id IN (SELECT show_seat_id FROM tickets WHERE booking_id=?)")) { s.setLong(1, bookingId); s.executeUpdate(); }
                try (PreparedStatement s = c.prepareStatement("UPDATE payments SET payment_status='REFUNDED' WHERE booking_id=? AND payment_status='SUCCESS'")) { s.setLong(1, bookingId); s.executeUpdate(); }
                c.commit();
                send(exchange, 200, "{\"status\":\"CANCELLED\",\"bookingId\":" + bookingId + "}");
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Booking cancellation failed: " + e.getSQLState());
            send(exchange, 500, "{\"error\":\"Could not cancel booking\"}");
        }
    }

    private static String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void handleBooking(
            HttpExchange exchange) throws IOException {

        try {
            String body = readBody(exchange.getRequestBody());



Long authenticatedUserId = AuthHandler.authenticatedUser(exchange);

if (authenticatedUserId == null) {
    send(exchange, 401,
            "{\"error\":\"Login required\"}");
    return;
}

long showId = getNumber(body, "showId");
long showSeatId = getNumber(body, "showSeatId");

if (showId <= 0 || showSeatId <= 0) {
    send(exchange, 400,
            "{\"error\":\"Show and seat IDs must be positive numbers\"}");
    return;
}

createBooking(exchange, authenticatedUserId, showId, showSeatId);
        } catch (IllegalArgumentException e) {
            send(exchange, 400,
                    "{\"error\":\"Invalid booking request\"}");
        }
    }

    private static void createBooking(
            HttpExchange exchange,
            long userId,
            long showId,
            long showSeatId) throws IOException {

        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {
                String seatStatus;
                BigDecimal basePrice;

                String checkSql =
                    "SELECT ss.seat_status, ms.base_price " +
                    "FROM show_seats ss " +
                    "JOIN movie_shows ms ON ms.show_id = ss.show_id " +
                    "WHERE ss.show_seat_id = ? " +
                    "AND ss.show_id = ? " +
                    "FOR UPDATE OF ss";

                try (PreparedStatement statement =
                             connection.prepareStatement(checkSql)) {

                    statement.setLong(1, showSeatId);
                    statement.setLong(2, showId);

                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            send(exchange, 404,
                                "{\"error\":\"Show seat not found\"}");
                            return;
                        }

                        seatStatus = rs.getString("seat_status");
                        basePrice = rs.getBigDecimal("base_price");
                    }
                }

                if (!"AVAILABLE".equalsIgnoreCase(seatStatus)) {
                    connection.rollback();
                    send(exchange, 409,
                        "{\"error\":\"Seat is not available\"}");
                    return;
                }

                String updateSeatSql =
                    "UPDATE show_seats " +
                    "SET seat_status = 'BOOKED' " +
                    "WHERE show_seat_id = ? " +
                    "AND show_id = ? " +
                    "AND seat_status = 'AVAILABLE'";

                try (PreparedStatement statement =
                             connection.prepareStatement(updateSeatSql)) {
                    statement.setLong(1, showSeatId);
                    statement.setLong(2, showId);

                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        send(exchange, 409,
                            "{\"error\":\"Seat is no longer available\"}");
                        return;
                    }
                }

                String insertBookingSql =
                    "INSERT INTO bookings " +
                    "(user_id, show_id, booking_time, " +
                    "booking_status, total_amount) " +
                    "VALUES (?, ?, CURRENT_TIMESTAMP, 'CONFIRMED', ?) RETURNING booking_id";

                long bookingId;
                try (PreparedStatement statement =
                             connection.prepareStatement(insertBookingSql)) {
                    statement.setLong(1, userId);
                    statement.setLong(2, showId);
                    statement.setBigDecimal(3, basePrice);
                    try (ResultSet generated = statement.executeQuery()) {
                        if (!generated.next()) throw new SQLException("Booking ID was not returned");
                        bookingId = generated.getLong(1);
                    }
                }

                String insertTicketSql =
                    "INSERT INTO tickets " +
                    "(booking_id, show_seat_id, ticket_price, ticket_status) " +
                    "VALUES (?, ?, ?, 'VALID') RETURNING ticket_id";

                long ticketId;
                try (PreparedStatement statement =
                             connection.prepareStatement(insertTicketSql)) {
                    statement.setLong(1, bookingId);
                    statement.setLong(2, showSeatId);
                    statement.setBigDecimal(3, basePrice);
                    try (ResultSet generated = statement.executeQuery()) {
                        if (!generated.next()) throw new SQLException("Ticket ID was not returned");
                        ticketId = generated.getLong(1);
                    }
                }

                String transactionRef =
                    "DEMO-TXN-" +
                    UUID.randomUUID().toString().substring(0, 8);

                String insertPaymentSql =
                    "INSERT INTO payments " +
                    "(booking_id, payment_method, payment_status, amount, " +
                    "transaction_ref, payment_time) " +
                    "VALUES (?, 'UPI', 'SUCCESS', ?, ?, CURRENT_TIMESTAMP) RETURNING payment_id";

                long paymentId;
                try (PreparedStatement statement =
                             connection.prepareStatement(insertPaymentSql)) {
                    statement.setLong(1, bookingId);
                    statement.setBigDecimal(2, basePrice);
                    statement.setString(3, transactionRef);
                    try (ResultSet generated = statement.executeQuery()) {
                        if (!generated.next()) throw new SQLException("Payment ID was not returned");
                        paymentId = generated.getLong(1);
                    }
                }

                connection.commit();

                String response =
                    "{\"status\":\"CONFIRMED\"," +
                    "\"bookingId\":" + bookingId + "," +
                    "\"ticketId\":" + ticketId + "," +
                    "\"paymentId\":" + paymentId + "," +
                    "\"showId\":" + showId + "," +
                    "\"showSeatId\":" + showSeatId + "," +
                    "\"amount\":" + basePrice.toPlainString() + "," +
                    "\"paymentMethod\":\"UPI\"," +
                    "\"paymentStatus\":\"SUCCESS\"," +
                    "\"demoPayment\":true}";

                send(exchange, 201, response);

            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    System.err.println("Booking rollback failed.");
                }

                System.err.println(
                    "Booking failed: " +
                    e.getClass().getSimpleName()
                );

                send(exchange, 500,
                    "{\"error\":\"Booking failed; transaction rolled back\"}");
            }

        } catch (SQLException e) {
            System.err.println(
                "Database connection failed: " +
                e.getClass().getSimpleName()
            );

            send(exchange, 500,
                "{\"error\":\"Database connection failed\"}");
        }
    }

    private static long getNumber(String body, String key) {
        Pattern pattern = Pattern.compile(
            "\"" + Pattern.quote(key) +
            "\"\\s*:\\s*(\\d+)"
        );

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            throw new IllegalArgumentException(
                "Missing field: " + key);
        }

        return Long.parseLong(matcher.group(1));
    }

    private static String readBody(InputStream input)
            throws IOException {

        byte[] bytes = input.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void send(
            HttpExchange exchange,
            int status,
            String body) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8");
        addCors(exchange);
        exchange.getResponseHeaders().set(
            "Cache-Control", "no-store");

        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);

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
