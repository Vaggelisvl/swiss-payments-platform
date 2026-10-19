package dev.vrailas.payments.contracts;

/** Names of the messaging topology. Producers publish to the exchange with a routing key. */
public final class Topology {

    public static final String EXCHANGE = "payments";
    public static final String DEAD_LETTER_EXCHANGE = "payments.dlx";

    public static final String PAYMENT_RECEIVED = "payment.received";
    public static final String PAYMENT_BOOKED = "payment.booked";
    public static final String PAYMENT_REJECTED = "payment.rejected";

    private Topology() {
    }
}
