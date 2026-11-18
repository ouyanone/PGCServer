package com.shiyuan.chat.springai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shiyuan.chat.ChatProperties;

/** Beans for the Spring AI edition of the club assistant. Built by hand: no Spring AI starters. */
@Configuration
public class SpringAiChatConfig {

    @Bean
    public SpringAiModels springAiModels(ChatProperties props) {
        return new SpringAiModels(props.getAnthropicApiKey(), props.getAnthropicBaseUrl(),
                props.getModel(), props.getMaxTokens());
    }
}
