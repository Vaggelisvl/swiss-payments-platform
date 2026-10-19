package dev.vrailas.payments.domain;

/** ISO 7064 MOD 97-10, used by IBANs and ISO 11649 creditor references. */
final class Iso7064 {

    private Iso7064() {
    }

    static int mod97(CharSequence value) {
        int remainder = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                remainder = (remainder * 10 + (c - '0')) % 97;
            } else if (c >= 'A' && c <= 'Z') {
                remainder = (remainder * 100 + (c - 'A' + 10)) % 97;
            } else {
                throw new DomainViolationException("unsupported character in checksum input");
            }
        }
        return remainder;
    }

    static boolean hasValidChecksum(String value) {
        return value.length() > 4 && mod97(value.substring(4) + value.substring(0, 4)) == 1;
    }
}
