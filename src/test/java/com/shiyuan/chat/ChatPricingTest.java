package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChatPricingTest {

    private final ChatPricing pricing = new ChatPricing(Map.of(
            "claude-sonnet-5-5", new ChatPricing.Price(new BigDecimal("2.00"), new BigDecimal("10.00"))));

    @Test
    void pricing_isBigDecimalPerMillion() {
        // 1,234 in at $2/M + 567 out at $10/M = 0.002468 + 0.00567 = 0.008138
        assertEquals(0, new BigDecimal("0.008138").compareTo(pricing.cost("claude-sonnet-5-5", 1234, 567)));
    }

    @Test
    void zeroTokens_costNothing() {
        assertEquals(0, BigDecimal.ZERO.compareTo(pricing.cost("claude-sonnet-5-5", 0, 0)));
    }

    /** A model with no price would silently spend uncounted money: refuse instead. */
    @Test
    void unknownModel_throws() {
        assertThrows(IllegalStateException.class, () -> pricing.cost("claude-unknown", 1, 1));
    }
}
