package com.shiyuan.chat;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    /** Days and months for limits and budget are the club's: New Jersey time. */
    @Bean
    public Clock chatClock() {
        return Clock.system(ZoneId.of("America/New_York"));
    }
}
