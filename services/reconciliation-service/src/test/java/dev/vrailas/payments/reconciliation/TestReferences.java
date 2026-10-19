package dev.vrailas.payments.reconciliation;

import java.util.concurrent.atomic.AtomicLong;

/** Generates valid, unique 27-digit QR references (modulo 10 recursive check digit). */
final class TestReferences {

    private static final int[] MOD10 = {0, 9, 4, 6, 8, 2, 7, 1, 3, 5};
    private static final AtomicLong SEQUENCE = new AtomicLong(System.nanoTime() % 1_000_000_000_000L);

    private TestReferences() {
    }

    static String qrr() {
        String digits = String.format("%026d", SEQUENCE.incrementAndGet());
        int carry = 0;
        for (char c : digits.toCharArray()) {
            carry = MOD10[(carry + c - '0') % 10];
        }
        return digits + (10 - carry) % 10;
    }
}
