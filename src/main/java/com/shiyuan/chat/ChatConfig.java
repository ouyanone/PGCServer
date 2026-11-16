package com.shiyuan.chat;

import java.time.Clock;
import java.time.ZoneId;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class ChatConfig {

    /** Days and months for limits and budget are the club's: New Jersey time. */
    @Bean
    public Clock chatClock() {
        return Clock.system(ZoneId.of("America/New_York"));
    }

    /**
     * The assistant, refusing to start with a model that has no price: its spend could not be
     * counted against the monthly budget.
     */
    @Bean
    public ChatAssistant chatAssistant(ChatProperties props) {
        Map<String, ChatPricing.Price> prices = new HashMap<>();
        props.getPrice().forEach((model, p) ->
                prices.put(model, new ChatPricing.Price(p.inputPerMtok(), p.outputPerMtok())));
        if (!prices.containsKey(props.getModel())) {
            throw new IllegalStateException("pgc.chat.price has no entry for model " + props.getModel());
        }
        ModelGateway gateway = new AnthropicModelGateway(props.getAnthropicApiKey(),
                props.getAnthropicBaseUrl(), props.getModel(), props.getMaxTokens());
        return new ChatAssistant(gateway, new ChatPricing(prices), props.getModel(), props.getMaxToolCalls());
    }
}
