
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CineReserveServer {

    public static void main(String[] args) throws IOException {

    HttpServer server = HttpServer.create(
            new InetSocketAddress("localhost", 8081), 0
    );

    BookingHandler.register(server);
    AuthHandler.register(server);

    // Keep your existing API registrations below this line.


server.createContext("/api/health", exchange -> {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
        sendResponse(exchange, 405,
                "{\"error\":\"GET method required\"}");
        return;
    }

    try (Connection connection = DatabaseConnection.getConnection()) {
        if (connection.isValid(5)) {
            sendResponse(exchange, 200,
                    "{\"status\":\"ok\","
                    + "\"service\":\"CineReserve API\","
                    + "\"database\":\"connected\"}");
        } else {
            sendResponse(exchange, 503,
                    "{\"status\":\"error\","
                    + "\"service\":\"CineReserve API\","
                    + "\"database\":\"unavailable\"}");
        }
    } catch (SQLException e) {
        System.err.println(
                "Health check failed: "
                + e.getClass().getSimpleName()
        );
        sendResponse(exchange, 503,
                "{\"status\":\"error\","
                + "\"service\":\"CineReserve API\","
                + "\"database\":\"unavailable\"}");
    }
});


        server.createContext("/api/shows", exchange -> {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if (!exchange.getRequestMethod().equals("GET")) {
                sendResponse(exchange, 405,
                        "{\"error\":\"GET method required\"}");
                return;
            }

            String sql =
                "SELECT show_id, movie_title, theatre_name, city, " +
                "screen_name, TO_CHAR(show_time, " +
                "'YYYY-MM-DD HH24:MI:SS') AS show_time, " +
                "total_seats, available_seats, base_price " +
                "FROM vw_show_availability " +
                "WHERE show_time >= CURRENT_TIMESTAMP " +
                "ORDER BY show_time";

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            try (Connection connection =
                         DatabaseConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }
                    first = false;

                    json.append("{")
                        .append("\"showId\":")
                        .append(rs.getLong("show_id")).append(",")
                        .append("\"movieTitle\":\"")
                        .append(escape(rs.getString("movie_title")))
                        .append("\",")
                        .append("\"theatre\":\"")
                        .append(escape(rs.getString("theatre_name")))
                        .append("\",")
                        .append("\"city\":\"")
                        .append(escape(rs.getString("city")))
                        .append("\",")
                        .append("\"screen\":\"")
                        .append(escape(rs.getString("screen_name")))
                        .append("\",")
                        .append("\"showTime\":\"")
                        .append(escape(rs.getString("show_time")))
                        .append("\",")
                        .append("\"totalSeats\":")
                        .append(rs.getInt("total_seats")).append(",")
                        .append("\"availableSeats\":")
                        .append(rs.getInt("available_seats")).append(",")
                        .append("\"basePrice\":")
                        .append(rs.getBigDecimal("base_price"))
                        .append("}");
                }

                json.append("]");
                sendResponse(exchange, 200, json.toString());

            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 500,
                        "{\"error\":\"Database query failed\"}");
            }
        });
        server.createContext("/api/movies", exchange -> {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }
            if (!exchange.getRequestMethod().equals("GET")) {
                sendResponse(exchange, 405,
                        "{\"error\":\"GET method required\"}");
                return;
            }

            String sql =
                "SELECT movie_id, title, genre, language, " +
                "duration_minutes, " +
                "EXTRACT(YEAR FROM release_date) AS release_year, " +
                "certificate, description " +
                "FROM Movies ORDER BY movie_id";

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            try (Connection connection =
                         DatabaseConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {
                    if (!first) {
                        json.append(",");
                    }
                    first = false;

                    json.append("{")
                        .append("\"id\":")
                        .append(rs.getLong("movie_id")).append(",")
                        .append("\"title\":\"")
                        .append(escape(rs.getString("title"))).append("\",")
                        .append("\"genre\":\"")
                        .append(escape(rs.getString("genre"))).append("\",")
                        .append("\"language\":\"")
                        .append(escape(rs.getString("language"))).append("\",")
                        .append("\"duration\":")
                        .append(rs.getInt("duration_minutes")).append(",")
                        .append("\"year\":")
                        .append(rs.getInt("release_year")).append(",")
                        .append("\"certificate\":\"")
                        .append(escape(rs.getString("certificate"))).append("\",")
                        .append("\"description\":\"")
                        .append(escape(rs.getString("description")))
                        .append("\"}");
                }

                json.append("]");
                sendResponse(exchange, 200, json.toString());

            } catch (SQLException e) {
                e.printStackTrace();
                sendResponse(exchange, 500,
                        "{\"error\":\"Movie query failed\"}");
            }
        });


        server.start();

        System.out.println(
                "CineReserve API running at http://localhost:8081"
        );
        System.out.println(
                "Health: http://localhost:8081/api/health"
        );
        System.out.println(
                "Shows:  http://localhost:8081/api/shows"
        );
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }

    private static void sendResponse(
            HttpExchange exchange, int status, String body
    ) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8"
        );
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (isLocalFrontend(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store"
        );

        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static boolean isLocalFrontend(String origin) {
        return origin != null && (origin.equals("null")
                || origin.matches("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?"));
    }
}
