package com.shiyuan.chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * {@link ModelGateway} over the official Anthropic Java SDK: plain Messages API, no streaming,
 * effort {@code low} (chat), thinking left at the model's default.
 *
 * <p>The client is built on first use, so the application starts -- and every other feature
 * works -- when no API key is configured; a question then fails as "unavailable".
 */
public class AnthropicModelGateway implements ModelGateway {

    /** Per call; with no retry this keeps a question inside ChatAssistant's 35 s deadline + one call. */
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final long maxTokens;
    private volatile AnthropicClient client;

    public AnthropicModelGateway(String apiKey, String baseUrl, String model, long maxTokens) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public Conversation start(String system, List<McpToolDef> tools, List<ChatExchangeDto> history, String question) {
        List<Tool> toolParams = tools.stream().map(AnthropicModelGateway::toTool).toList();
        List<MessageParam> transcript = new ArrayList<>();
        for (ChatExchangeDto past : history) {
            transcript.add(MessageParam.ofUser(past.question()));
            transcript.add(MessageParam.ofAssistant(past.answer()));
        }
        transcript.add(MessageParam.ofUser(question));
        return new SdkConversation(system, toolParams, transcript);
    }

    private final class SdkConversation implements Conversation {

        private final String system;
        private final List<Tool> tools;
        private final List<MessageParam> transcript;

        SdkConversation(String system, List<Tool> tools, List<MessageParam> transcript) {
            this.system = system;
            this.tools = tools;
            this.transcript = transcript;
        }

        @Override
        public Reply send() {
            MessageCreateParams.Builder params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(system)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .messages(transcript);
            tools.forEach(params::addTool);

            Message response;
            try {
                response = client().messages().create(params.build());
            } catch (AnthropicException e) {
                // The SDK's message can echo request details; keep only the type.
                throw new ModelUnavailableException("Claude API: " + e.getClass().getSimpleName());
            }

            // Append the model's own turn as-is: thinking blocks must go back unchanged.
            transcript.add(response.toParam());

            StringBuilder text = new StringBuilder();
            List<ToolCall> calls = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.text().ifPresent(t -> text.append(t.text()));
                block.toolUse().ifPresent(t -> calls.add(new ToolCall(t.id(), t.name(),
                        t._input().convert(new TypeReference<Map<String, Object>>() {}))));
            }
            String stop = response.stopReason().map(r -> r.asString()).orElse("");
            return new Reply(text.toString(), calls, stop,
                    response.usage().inputTokens(), response.usage().outputTokens());
        }

        @Override
        public void addToolResults(List<ToolResult> results) {
            List<ContentBlockParam> blocks = new ArrayList<>();
            for (ToolResult r : results) {
                blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(r.toolUseId())
                        .content(r.text())
                        .isError(r.isError())
                        .build()));
            }
            transcript.add(MessageParam.ofUserOfContentBlockParams(blocks));
        }
    }

    private AnthropicClient client() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ModelUnavailableException("Claude API key is not configured");
        }
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                            .apiKey(apiKey)
                            .timeout(TIMEOUT)
                            .maxRetries(0);
                    if (baseUrl != null && !baseUrl.isBlank()) {
                        b.baseUrl(baseUrl);
                    }
                    client = b.build();
                }
                c = client;
            }
        }
        return c;
    }

    /** An MCP tool's JSON Schema, carried over key by key. */
    @SuppressWarnings("unchecked")
    static Tool toTool(McpToolDef def) {
        Tool.InputSchema.Builder schema = Tool.InputSchema.builder();
        for (Map.Entry<String, Object> e : def.inputSchema().entrySet()) {
            switch (e.getKey()) {
                case "type" -> { /* always "object"; the SDK sets it */ }
                case "properties" -> {
                    Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
                    ((Map<String, Object>) e.getValue()).forEach((k, v) -> props.putAdditionalProperty(k, JsonValue.from(v)));
                    schema.properties(props.build());
                }
                case "required" -> schema.required((List<String>) e.getValue());
                default -> schema.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
            }
        }
        return Tool.builder()
                .name(def.name())
                .description(def.description())
                .inputSchema(schema.build())
                .build();
    }
}
