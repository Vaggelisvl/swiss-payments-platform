package dev.vrailas.payments.processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/** {@code processor.*}: business rule configuration. */
@ConfigurationProperties(prefix = "processor")
public record ProcessorProperties(BigDecimal maxAmount, List<String> denylist) {

    public ProcessorProperties {
        maxAmount = maxAmount == null ? new BigDecimal("100000.00") : maxAmount;
        denylist = denylist == null ? List.of() : List.copyOf(denylist);
    }
}
