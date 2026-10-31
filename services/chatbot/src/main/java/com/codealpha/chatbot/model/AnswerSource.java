package com.codealpha.chatbot.model;

/** Where an answer came from: a fixed response, or a lookup against another service. */
public enum AnswerSource {
    STATIC,
    LIVE
}
