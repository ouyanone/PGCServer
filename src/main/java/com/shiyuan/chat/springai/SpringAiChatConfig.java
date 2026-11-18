package com.shiyuan.chat.springai;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shiyuan.chat.ChatPricing;
import com.shiyuan.chat.ChatProperties;

/** Beans for the Spring AI edition of the club assistant. Built by hand: no Spring AI starters. */
@Configuration
public class SpringAiChatConfig {

    static final Duration QUESTION_DEADLINE = Duration.ofSeconds(35);

    @Bean
    public SpringAiModels springAiModels(ChatProperties props) {
        return new SpringAiModels(props.getAnthropicApiKey(), props.getAnthropicBaseUrl(),
                props.getModel(), props.getMaxTokens());
    }

    @Bean
    public SpringAiChatAssistant springAiChatAssistant(SpringAiModels models, ChatProperties props, Clock chatClock,
            @Value("${pgc.chat.mcp-url:https://pgcgolf.club/mcp}") String mcpUrl) {
        Map<String, ChatPricing.Price> prices = new HashMap<>();
        props.getPrice().forEach((model, p) ->
                prices.put(model, new ChatPricing.Price(p.inputPerMtok(), p.outputPerMtok())));
        return new SpringAiChatAssistant(models, new ChatPricing(prices), props.getModel(),
                props.getMaxToolCalls(), mcpUrl, chatClock, QUESTION_DEADLINE);
    }
}
