package com.codealpha.chatbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.codealpha.chatbot.model.ChatRequest;
import com.codealpha.chatbot.model.ChatResponse;
import com.codealpha.chatbot.service.ChatService;
import com.codealpha.chatbot.service.ExternalKnowledgeService;
import com.codealpha.chatbot.service.IntentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class ChatbotApplication {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("CHATBOT_PORT", "8090"));

        IntentService intentService = new IntentService();
        ExternalKnowledgeService externalKnowledgeService = new ExternalKnowledgeService(MAPPER);
        ChatService chatService = new ChatService(intentService, externalKnowledgeService);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/chat", exchange -> handleChat(exchange, chatService));
        server.createContext("/", ChatbotApplication::serveStatic);
        server.setExecutor(null);
        server.start();

        System.out.println("Chatbot started on http://localhost:" + port);
    }

    private static void handleChat(HttpExchange exchange, ChatService chatService) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, new ChatResponse("ERROR", "Method not allowed"));
            return;
        }

        try (InputStream input = exchange.getRequestBody()) {
            ChatRequest request = MAPPER.readValue(input, ChatRequest.class);
            ChatResponse response = chatService.answer(request.getMessage());
            writeJson(exchange, 200, response);
        } catch (Exception ex) {
            writeJson(exchange, 400, new ChatResponse("ERROR", "Invalid request payload"));
        }
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path)) {
            path = "/index.html";
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

    private static void writeJson(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
