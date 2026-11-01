package com.codealpha.chatbot.model;

public class ChatResponse {
    private String intent;
    private String response;
    private AnswerSource source;

    public ChatResponse() {
    }

    public ChatResponse(Intent intent, String response, AnswerSource source) {
        this.intent = intent.name();
        this.response = response;
        this.source = source;
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

    public AnswerSource getSource() {
        return source;
    }

    public void setSource(AnswerSource source) {
        this.source = source;
    }
}
