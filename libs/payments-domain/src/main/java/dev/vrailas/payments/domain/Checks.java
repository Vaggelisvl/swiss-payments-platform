package dev.vrailas.payments.domain;

final class Checks {

    private Checks() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainViolationException(message);
        }
    }
}
