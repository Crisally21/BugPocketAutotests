package ru.bugpocket.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstTest {

    @Test
    void shouldCalculateRemainingAttachmentQuota() {
        long totalQuota = 25_000_000L;
        long usedBytes = 5_000_000L;

        long remainingBytes = totalQuota - usedBytes;
        assertEquals(19_000_000L, remainingBytes);
    }
}
