package com.shiyuan.chat.springai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import com.shiyuan.chat.ModelUnavailableException;

/**
 * Spring AI's Anthropic chat model, built on first use so a missing API key never stops the
 * app from starting (the Spring AI starter would demand the key at start-up).
 *
 * <p>One attempt per call, a 20 s read timeout, and tool execution switched off: the tool loop
 * belongs to {@link SpringAiChatAssistant}, which enforces the tool-call limit and deadline.
 */
public final class SpringAiModels implements Supplier<ChatModel> {

    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final long maxTokens;
    private volatile ChatModel chatModel;

    public SpringAiModels(String apiKey, String baseUrl, String model, long maxTokens) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public ChatModel get() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ModelUnavailableException("Claude API key is not configured");
        }
        ChatModel m = chatModel;
        if (m == null) {
            synchronized (this) {
                if (chatModel == null) {
                    chatModel = build();
                }
                m = chatModel;
            }
        }
        return m;
    }

    private ChatModel build() {
        JdkClientHttpRequestFactory http = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        http.setReadTimeout(READ_TIMEOUT);

        AnthropicApi.Builder api = AnthropicApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder().requestFactory(http));
        if (baseUrl != null && !baseUrl.isBlank()) {
            api.baseUrl(baseUrl);
        }

        return AnthropicChatModel.builder()
                .anthropicApi(api.build())
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(model)
                        .maxTokens(Math.toIntExact(maxTokens))
                        .internalToolExecutionEnabled(false)
                        .build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
    }
}
