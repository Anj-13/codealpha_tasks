package com.dataredundancy.removalsystem.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dataredundancy.removalsystem.exception.AdminKeyRejectedException;

@Component
public class AdminAccess {

    private final byte[] adminKey;

    public AdminAccess(@Value("${app.admin-key:}") String adminKey) {
        this.adminKey = adminKey.getBytes(StandardCharsets.UTF_8);
    }

    /** No key means masked access. A wrong key is rejected, and so is any key when none is configured. */
    public boolean grantsFullAccess(String suppliedKey) {
        if (suppliedKey == null) {
            return false;
        }
        boolean matches = adminKey.length > 0
                && MessageDigest.isEqual(adminKey, suppliedKey.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new AdminKeyRejectedException();
        }
        return true;
    }
}
