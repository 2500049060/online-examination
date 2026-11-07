import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Simple Java HTTP server for the Online Examination Website.
 * Requires Java 11 or newer. Demo credentials: student / 1234.
 */
public class OnlineExamServer {
    private static final int PORT = 8080;
    private static final String DEMO_ID = "student";
    private static final String DEMO_PASSWORD = "1234";

    // Correct option indexes are kept on the server, not in the browser.
    private static final int[] CORRECT_ANSWERS = {1, 0, 1, 1, 2};

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/login", OnlineExamServer::login);
        server.createContext("/api/submit", OnlineExamServer::submit);
        server.createContext("/", OnlineExamServer::serveStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("Online Examination Website is running.");
        System.out.println("Open http://localhost:" + PORT + " in your browser.");
        System.out.println("Demo login: student / 1234");
    }

    private static void login(HttpExchange exchange) throws IOException {
        if (!allowPost(exchange)) return;
        Map<String, String> form = readForm(exchange);
        String id = form.getOrDefault("studentId", "");
        String password = form.getOrDefault("password", "");

        if (DEMO_ID.equals(id) && DEMO_PASSWORD.equals(password)) {
            sendJson(exchange, 200, "{\"success\":true,\"message\":\"Login successful\"}");
        } else {
            sendJson(exchange, 401, "{\"success\":false,\"message\":\"Invalid Student ID or password.\"}");
        }
    }

    private static void submit(HttpExchange exchange) throws IOException {
        if (!allowPost(exchange)) return;
        Map<String, String> form = readForm(exchange);
        String id = form.getOrDefault("studentId", "");
        if (!DEMO_ID.equals(id)) {
            sendJson(exchange, 401, "{\"message\":\"Please log in again.\"}");
            return;
        }

        String[] submitted = form.getOrDefault("answers", "").split(",", -1);
        if (submitted.length != CORRECT_ANSWERS.length) {
            sendJson(exchange, 400, "{\"message\":\"Invalid answer sheet.\"}");
            return;
        }

        int correct = 0;
        for (int i = 0; i < CORRECT_ANSWERS.length; i++) {
            try {
                if (Integer.parseInt(submitted[i]) == CORRECT_ANSWERS[i]) correct++;
            } catch (NumberFormatException ignored) {
                // -1 or invalid input is counted as unanswered/incorrect.
            }
        }

        int total = CORRECT_ANSWERS.length;
        int percentage = correct * 100 / total;
        String safeId = jsonEscape(id);
        String result = String.format(
            Locale.ROOT,
            "{\"studentId\":\"%s\",\"score\":%d,\"total\":%d,\"correct\":%d,\"percentage\":%d}",
            safeId, correct, total, correct, percentage
        );
        sendJson(exchange, 200, result);
    }

    private static boolean allowPost(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"POST method required.\"}");
            return false;
        }
        return true;
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1
                ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"message\":\"Method not allowed.\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path publicDir = Paths.get("public").toAbsolutePath().normalize();
        Path file = publicDir.resolve(path.substring(1)).normalize();

        if (!file.startsWith(publicDir) || !Files.exists(file) || Files.isDirectory(file)) {
            sendText(exchange, 404, "Page not found", "text/plain; charset=UTF-8");
            return;
        }

        String name = file.getFileName().toString();
        String contentType = name.endsWith(".css") ? "text/css; charset=UTF-8"
            : name.endsWith(".js") ? "application/javascript; charset=UTF-8"
            : "text/html; charset=UTF-8";
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, json, "application/json; charset=UTF-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text, String type) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
