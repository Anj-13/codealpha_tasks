package com.codealpha.chatbot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MessageDetailsTest {

    @Test
    void readsATicketNumberAndEmail() {
        MessageDetails details = MessageDetails.from("check booking 123E4567-E89B-12D3-A456-426614174000 pax@example.com");

        assertEquals("123e4567-e89b-12d3-a456-426614174000", details.ticketNumber());
        assertEquals("pax@example.com", details.email());
    }

    @Test
    void readsARecordToValidate() {
        MessageDetails details = MessageDetails.from("validate Ava Thompson ava@example.com 07123 456 789");

        assertEquals("Ava Thompson", details.name());
        assertEquals("ava@example.com", details.email());
        assertEquals("07123 456 789", details.phone());
    }

    @Test
    void acceptsCommandWordsBeforeTheName() {
        assertEquals("Ava", MessageDetails.from("check a record Ava ava@example.com 07123456789").name());
    }

    @Test
    void leavesMissingDetailsEmpty() {
        MessageDetails details = MessageDetails.from("validate");

        assertNull(details.ticketNumber());
        assertNull(details.email());
        assertNull(details.phone());
        assertNull(details.name());
    }

    @Test
    void doesNotMistakeATicketNumberForAPhone() {
        assertNull(MessageDetails.from("123e4567-e89b-12d3-a456-426614174000").phone());
    }
}
