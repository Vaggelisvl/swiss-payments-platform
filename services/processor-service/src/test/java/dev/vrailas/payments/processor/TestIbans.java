package dev.vrailas.payments.processor;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Generates valid, unique Swiss IBANs so that integration tests never collide. */
final class TestIbans {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private TestIbans() {
    }

    static String next() {
        String bban = String.format("00762%012d", SEQUENCE.incrementAndGet() % 1_000_000_000_000L);
        // CH = 12 17, check digits placeholder 00
        BigInteger numeric = new BigInteger(bban + "121700");
        int check = 98 - numeric.mod(BigInteger.valueOf(97)).intValue();
        return String.format("CH%02d%s", check, bban);
    }
}
