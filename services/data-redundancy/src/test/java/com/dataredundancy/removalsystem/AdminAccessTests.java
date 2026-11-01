package com.dataredundancy.removalsystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.dataredundancy.removalsystem.Service.AdminAccess;
import com.dataredundancy.removalsystem.exception.AdminKeyRejectedException;

class AdminAccessTests {

    @Test
    void noKeySuppliedMeansMaskedAccess() {
        assertThat(new AdminAccess("secret").grantsFullAccess(null)).isFalse();
    }

    @Test
    void matchingKeyGrantsFullAccess() {
        assertThat(new AdminAccess("secret").grantsFullAccess("secret")).isTrue();
    }

    @Test
    void wrongKeyIsRejected() {
        assertThatThrownBy(() -> new AdminAccess("secret").grantsFullAccess("guess"))
            .isInstanceOf(AdminKeyRejectedException.class);
    }

    @Test
    void anyKeyIsRejectedWhenNoneIsConfigured() {
        assertThatThrownBy(() -> new AdminAccess("").grantsFullAccess(""))
            .isInstanceOf(AdminKeyRejectedException.class);
    }
}
