package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChatConfigTest {

    private static ChatProperties props(String model) {
        ChatProperties p = new ChatProperties();
        p.setModel(model);
        p.setPrice(Map.of("claude-sonnet-5-5",
                new ChatProperties.Price(new BigDecimal("2.00"), new BigDecimal("10.00"))));
        return p;
    }

    /** An unpriced model would make every answer uncountable against the budget: refuse to start. */
    @Test
    void unpricedModel_failsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new ChatConfig().chatAssistant(props("claude-unpriced")));
    }

    @Test
    void pricedModel_builds() {
        assertNotNull(new ChatConfig().chatAssistant(props("claude-sonnet-5-5")));
    }
}
