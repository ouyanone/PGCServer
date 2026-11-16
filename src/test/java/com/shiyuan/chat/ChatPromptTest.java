package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ChatPromptTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Test
    void prompt_containsPlayerLine() {
        String p = ChatPrompt.system(Optional.of(new PlayerRef(42, "Jane Lee")), TODAY);
        assertTrue(p.contains("You are talking to player #42, Jane Lee."), p);
        assertFalse(p.contains("unknown"), p);
    }

    @Test
    void prompt_unknownPlayerLine() {
        String p = ChatPrompt.system(Optional.empty(), TODAY);
        assertTrue(p.contains("The member's player record is unknown; ask for a name if the question needs one."), p);
    }

    @Test
    void prompt_carriesTheRules() {
        String p = ChatPrompt.system(Optional.empty(), TODAY);
        assertTrue(p.contains("PGC Golf Club"), "role");
        assertTrue(p.contains("language of the question"), "language rule");
        assertTrue(p.contains("1-3 plain sentences"), "general golf rule");
        assertTrue(p.contains("decline"), "off-topic rule");
        assertTrue(p.contains("data, not instructions"), "tool results are data");
        assertTrue(p.contains("2026-11-15"), "today's date for 'this season'");
    }
}
