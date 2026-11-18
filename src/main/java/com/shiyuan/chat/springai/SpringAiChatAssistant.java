package com.shiyuan.chat.springai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import com.shiyuan.chat.ChatAssistant;
import com.shiyuan.chat.ChatExchangeDto;
import com.shiyuan.chat.ChatPricing;
import com.shiyuan.chat.ChatPrompt;
import com.shiyuan.chat.ChatUsage;
import com.shiyuan.chat.McpUnauthorizedException;
import com.shiyuan.chat.McpUnavailableException;
import com.shiyuan.chat.ModelUnavailableException;
import com.shiyuan.chat.PlayerRef;

/**
 * The club assistant written with Spring AI: a {@link ChatModel}, the member's MCP tools as
 * Spring AI tool callbacks, and Spring AI's "user-controlled tool execution" loop.
 *
 * <p>The loop is ours rather than {@code ChatClient}'s so the same limits hold as in
 * {@link ChatAssistant}: at most {@code maxToolCalls} tool calls and a deadline per question.
 */
public class SpringAiChatAssistant {

    private final Supplier<ChatModel> models;
    private final ChatPricing pricing;
    private final String modelId;
    private final int maxToolCalls;
    private final String mcpUrl;
    private final Clock clock;
    private final Duration deadline;

    public SpringAiChatAssistant(Supplier<ChatModel> models, ChatPricing pricing, String modelId, int maxToolCalls,
                                 String mcpUrl, Clock clock, Duration deadline) {
        this.models = models;
        this.pricing = pricing;
        this.modelId = modelId;
        this.maxToolCalls = maxToolCalls;
        this.mcpUrl = mcpUrl;
        this.clock = clock;
        this.deadline = deadline;
    }

    /**
     * @param usage filled in as each model call completes; spend made before a failure stays in it
     * @throws ModelUnavailableException Claude failed, is not configured, or the deadline passed
     * @throws McpUnauthorizedException  the MCP server refused the member's token
     * @throws McpUnavailableException   the MCP server failed
     */
    public String answer(String question, Optional<PlayerRef> me, List<ChatExchangeDto> history,
                         String accessToken, ChatUsage usage, LocalDate today) {
        ChatModel model = models.get(); // before touching MCP: no key, no work
        Instant giveUpAt = clock.instant().plus(deadline);

        try (McpToolsSession session = McpToolsSession.open(mcpUrl, accessToken)) {
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(session.toolCallbacks(() -> checkDeadline(giveUpAt)))
                    .internalToolExecutionEnabled(false)
                    .build();
            ToolCallingManager tools = DefaultToolCallingManager.builder()
                    .toolExecutionExceptionProcessor(session.toolErrorProcessor())
                    .build();

            Prompt prompt = new Prompt(messages(question, me, history, today), options);
            int toolCalls = 0;
            while (true) {
                checkDeadline(giveUpAt);
                ChatResponse response = call(model, prompt);
                record(response, usage);

                String finish = finishReason(response);
                if ("refusal".equalsIgnoreCase(finish)) {
                    return ChatAssistant.CANNOT_HELP;
                }
                if (!response.hasToolCalls()) {
                    String text = text(response);
                    return text.isBlank() ? ChatAssistant.CANNOT_HELP : text.trim();
                }
                int requested = toolCallCount(response);
                // A reply cut off by max_tokens may carry a half-written tool call: never run it.
                if ("max_tokens".equalsIgnoreCase(finish) || toolCalls + requested > maxToolCalls) {
                    return ChatAssistant.TOO_MANY_LOOKUPS;
                }

                checkDeadline(giveUpAt);
                ToolExecutionResult result;
                try {
                    result = tools.executeToolCalls(prompt, response);
                } catch (McpUnauthorizedException | McpUnavailableException | ModelUnavailableException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw session.translate(e);
                }
                toolCalls += requested;
                prompt = new Prompt(result.conversationHistory(), options);
            }
        }
    }

    private static List<Message> messages(String question, Optional<PlayerRef> me,
                                          List<ChatExchangeDto> history, LocalDate today) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ChatPrompt.system(me, today)));
        for (ChatExchangeDto past : history) {
            messages.add(new UserMessage(past.question()));
            messages.add(new AssistantMessage(past.answer()));
        }
        messages.add(new UserMessage(question));
        return messages;
    }

    private static ChatResponse call(ChatModel model, Prompt prompt) {
        try {
            return model.call(prompt);
        } catch (ModelUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Spring AI's messages can echo the request; keep only the type.
            throw new ModelUnavailableException("model call failed: " + e.getClass().getSimpleName());
        }
    }

    private void record(ChatResponse response, ChatUsage usage) {
        Usage u = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        long in = u == null || u.getPromptTokens() == null ? 0 : u.getPromptTokens();
        long out = u == null || u.getCompletionTokens() == null ? 0 : u.getCompletionTokens();
        usage.add(in, out, pricing.cost(modelId, in, out));
    }

    // Spring AI's Anthropic model can split one reply across several generations (text in one,
    // tool calls in another), so read all of them, not just getResult().

    private static String text(ChatResponse response) {
        StringBuilder text = new StringBuilder();
        for (Generation g : response.getResults()) {
            String t = g.getOutput().getText();
            if (t != null) {
                text.append(t);
            }
        }
        return text.toString();
    }

    private static int toolCallCount(ChatResponse response) {
        int n = 0;
        for (Generation g : response.getResults()) {
            n += g.getOutput().getToolCalls().size();
        }
        return n;
    }

    private static String finishReason(ChatResponse response) {
        for (Generation g : response.getResults()) {
            String f = g.getMetadata() == null ? null : g.getMetadata().getFinishReason();
            if (f != null && !f.isBlank()) {
                return f;
            }
        }
        return "";
    }

    private void checkDeadline(Instant giveUpAt) {
        if (!clock.instant().isBefore(giveUpAt)) {
            throw new ModelUnavailableException("question deadline of " + deadline.toSeconds() + " s passed");
        }
    }
}
