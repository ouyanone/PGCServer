package com.shiyuan.chat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Dollar cost of a model call, from per-million-token prices held in configuration. */
public class ChatPricing {

    public record Price(BigDecimal inputPerMTok, BigDecimal outputPerMTok) {}

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final Map<String, Price> prices;

    public ChatPricing(Map<String, Price> prices) {
        this.prices = Map.copyOf(prices);
    }

    public BigDecimal cost(String model, long inputTokens, long outputTokens) {
        Price p = prices.get(model);
        if (p == null) {
            throw new IllegalStateException("no price configured for model " + model);
        }
        BigDecimal in = p.inputPerMTok().multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal out = p.outputPerMTok().multiply(BigDecimal.valueOf(outputTokens));
        return in.add(out).divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
