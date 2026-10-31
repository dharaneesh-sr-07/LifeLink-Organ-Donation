import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final String DB_URL = "jdbc:sqlite:organ_donation.db";

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        createTable();

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", Main::handleRequest);
        server.start();

        System.out.println("Donor table created successfully!");
        System.out.println("Website started successfully!");
        System.out.println("Open: http://localhost:8080");
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS donors (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "age INTEGER NOT NULL," +
                "blood_group TEXT NOT NULL," +
                "organ TEXT NOT NULL," +
                "city TEXT NOT NULL," +
                "phone TEXT NOT NULL)";
        try (Connection con = connect(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

    private static void handleRequest(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        if ("/".equals(path) && "GET".equalsIgnoreCase(method)) {
            sendFile(ex, "web/index.html", "text/html; charset=UTF-8");
        } else if ("/style.css".equals(path) && "GET".equalsIgnoreCase(method)) {
            sendFile(ex, "web/style.css", "text/css; charset=UTF-8");
        } else if ("/register".equals(path) && "POST".equalsIgnoreCase(method)) {
            register(ex);
        } else if ("/donors".equals(path) && "GET".equalsIgnoreCase(method)) {
            showDonors(ex);
        } else {
            send(ex, 404, "<h1>Page not found</h1><p><a href='/'>Return home</a></p>");
        }
    }

    private static void sendFile(HttpExchange ex, String fileName, String contentType) throws IOException {
        Path path = Paths.get(fileName);
        if (!Files.exists(path)) {
            send(ex, 404, "<h1>File not found</h1><p>Missing: " + escape(fileName) + "</p>");
            return;
        }
        byte[] data = Files.readAllBytes(path);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private static void register(HttpExchange ex) throws IOException {
        try {
            Map<String, String> form = readForm(ex);
            String name = required(form, "name");
            int age = Integer.parseInt(required(form, "age"));
            String blood = required(form, "blood");
            String organ = required(form, "organ");
            String city = required(form, "city");
            String phone = required(form, "phone");

            if (age < 18 || age > 100) {
                send(ex, 400, "<h2>Please enter an age between 18 and 100.</h2><p><a href='/'>Go back</a></p>");
                return;
            }

            String sql = "INSERT INTO donors(name, age, blood_group, organ, city, phone) VALUES(?,?,?,?,?,?)";
            try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setInt(2, age);
                ps.setString(3, blood);
                ps.setString(4, organ);
                ps.setString(5, city);
                ps.setString(6, phone);
                ps.executeUpdate();
            }
            send(ex, 200, "<h2>Registration saved successfully!</h2>" +
                    "<p>Your details have been saved in the project database.</p>" +
                    "<p><a href='/'>Register another donor</a> | <a href='/donors'>View donor list</a></p>");
        } catch (NumberFormatException e) {
            send(ex, 400, "<h2>Invalid age.</h2><p>Please enter a valid number.</p><a href='/'>Go back</a>");
        } catch (IllegalArgumentException e) {
            send(ex, 400, "<h2>Missing information.</h2><p>" + escape(e.getMessage()) + "</p><a href='/'>Go back</a>");
        } catch (SQLException e) {
            e.printStackTrace();
            send(ex, 500, "<h2>Database error.</h2><p>Could not save the details.</p><a href='/'>Go back</a>");
        }
    }

    private static String required(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Please fill in all fields.");
        }
        return value.trim();
    }

    private static void showDonors(HttpExchange ex) throws IOException {
        StringBuilder rows = new StringBuilder();
        try (Connection con = connect(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, age, blood_group, organ, city FROM donors ORDER BY id DESC")) {
            while (rs.next()) {
                rows.append("<tr><td>").append(escape(rs.getString("name"))).append("</td><td>")
                    .append(rs.getInt("age")).append("</td><td>")
                    .append(escape(rs.getString("blood_group"))).append("</td><td>")
                    .append(escape(rs.getString("organ"))).append("</td><td>")
                    .append(escape(rs.getString("city"))).append("</td></tr>");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            send(ex, 500, "<h2>Unable to read donor records.</h2><a href='/'>Back to Home</a>");
            return;
        }

        if (rows.length() == 0) {
            rows.append("<tr><td colspan='5'>No donor details have been registered yet.</td></tr>");
        }
        String body = "<h1>Registered Donors</h1>" +
                "<p class='intro'>Donor interest records stored in this demonstration database.</p>" +
                "<div class='table-wrap'><table><tr><th>Name</th><th>Age</th><th>Blood Group</th><th>Organ</th><th>City</th></tr>" +
                rows + "</table></div><p><a href='/'>Back to Home</a></p>";
        send(ex, 200, body);
    }

    private static Map<String, String> readForm(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                           URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        String page = "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>LifeLink - Organ Donation</title><link rel='stylesheet' href='/style.css'>" +
                "</head><body><header><h2>♥ LifeLink</h2><nav><a href='/'>Home</a><a href='/donors'>View Donors</a></nav></header>" +
                "<main class='card'>" + body + "</main></body></html>";
        byte[] data = page.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
