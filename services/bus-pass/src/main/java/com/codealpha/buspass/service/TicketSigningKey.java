package com.codealpha.buspass.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The key that signs tickets. It stays outside the database, so editing a ticket row is not
 * enough to forge one. Without TICKET_SIGNING_SECRET, a local key file keeps tickets valid
 * across restarts.
 */
@Component
public class TicketSigningKey {

    private static final Logger log = LoggerFactory.getLogger(TicketSigningKey.class);
    private static final int KEY_BYTES = 32;

    private final byte[] key;

    public TicketSigningKey(@Value("${ticket.signing-secret:}") String secret,
                            @Value("${ticket.signing-key-file:${user.home}/.buspass-signing-key}") String keyFile)
        throws IOException {
        this.key = secret.isBlank() ? loadOrCreate(Path.of(keyFile)) : secret.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] bytes() {
        return key.clone();
    }

    private static byte[] loadOrCreate(Path file) throws IOException {
        if (Files.exists(file)) {
            return Files.readAllBytes(file);
        }

        byte[] generated = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(generated);
        Files.write(file, generated, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        log.warn("TICKET_SIGNING_SECRET is not set, so a local signing key was created at {}", file);
        return generated;
    }
}
