package com.codealpha.chatbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.codealpha.chatbot.model.ChatRequest;
import com.codealpha.chatbot.model.ChatResponse;
import com.codealpha.chatbot.service.ChatService;
import com.codealpha.chatbot.service.ExternalKnowledgeService;
import com.codealpha.chatbot.service.IntentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class ChatbotApplication {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_BODY_BYTES = 4096;
    private static final int MAX_MESSAGE_LENGTH = 500;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("CHATBOT_PORT", "8090"));
        ChatService chatService = new ChatService(new IntentService(), new ExternalKnowledgeService(MAPPER));
        start(port, chatService);
        System.out.println("Chatbot started on http://localhost:" + port);
    }

    static HttpServer start(int port, ChatService chatService) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/chat", exchange -> handleChat(exchange, chatService));
        server.createContext("/actuator/health", ChatbotApplication::handleHealth);
        server.createContext("/", ChatbotApplication::serveStatic);
        // Live lookups can wait on other services, so one slow request must not block the rest.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        writeJson(exchange, 200, Map.of("status", "UP"));
    }

    private static void handleChat(HttpExchange exchange, ChatService chatService) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeError(exchange, 405, "Method Not Allowed", "Use POST with a JSON body");
            return;
        }

        ChatRequest request;
        try (InputStream input = exchange.getRequestBody()) {
            byte[] body = input.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                writeError(exchange, 413, "Content Too Large", "Request body is too large");
                return;
            }
            request = MAPPER.readValue(body, ChatRequest.class);
        } catch (IOException ex) {
            writeError(exchange, 400, "Bad Request", "Request body is missing or malformed");
            return;
        }

        String message = request == null ? null : request.getMessage();
        if (message == null || message.isBlank()) {
            writeError(exchange, 400, "Bad Request", "Message is required");
        } else if (message.length() > MAX_MESSAGE_LENGTH) {
            writeError(exchange, 400, "Bad Request", "Message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        } else {
            ChatResponse response = chatService.answer(message);
            writeJson(exchange, 200, response);
        }
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path)) {
            path = "/index.html";
        }

        if (path.contains("..")) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        String resourcePath = "/web" + path;
        String contentType = "text/html; charset=UTF-8";
        if (path.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            contentType = "application/javascript; charset=UTF-8";
        }

        try (InputStream input = ChatbotApplication.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private static void writeError(HttpExchange exchange, int status, String error, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        writeJson(exchange, status, body);
    }

    private static void writeJson(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
