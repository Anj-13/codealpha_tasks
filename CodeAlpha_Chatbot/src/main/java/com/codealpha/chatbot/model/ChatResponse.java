package com.codealpha.chatbot.model;

public class ChatResponse {
    private String intent;
    private String response;

    public ChatResponse() {
    }

    public ChatResponse(String intent, String response) {
        this.intent = intent;
        this.response = response;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
