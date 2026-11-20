package com.shiyuan.chat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code pgc.chat.*}: the club assistant's model, limits, prices and API key. */
@ConfigurationProperties(prefix = "pgc.chat")
public class ChatProperties {

    public record Price(BigDecimal inputPerMtok, BigDecimal outputPerMtok) {}

    private String model = "claude-sonnet-5-5";
    private long maxTokens = 2048;
    private int maxToolCalls = 5;
    /** Club admins set up tournaments in one message, which takes many more tool calls. */
    private int adminMaxToolCalls = 15;
    /**
     * nginx gives the website 60 s. The deadline is checked before each call and a call may then
     * take up to 20 s, so 35 + 20 leaves a margin. A long setup that runs out says what was done,
     * and the admin continues in a second message (repeated creates are refused).
     */
    private int adminDeadlineSeconds = 35;
    /** Read from /etc/pgc/chatbot.properties in production; never in git. */
    private String anthropicApiKey = "";
    /** Empty means the SDK's default (api.anthropic.com). */
    private String anthropicBaseUrl = "";
    private Map<String, Price> price = new HashMap<>();

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getMaxTokens() { return maxTokens; }
    public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
    public int getAdminMaxToolCalls() { return adminMaxToolCalls; }
    public void setAdminMaxToolCalls(int adminMaxToolCalls) { this.adminMaxToolCalls = adminMaxToolCalls; }
    public int getAdminDeadlineSeconds() { return adminDeadlineSeconds; }
    public void setAdminDeadlineSeconds(int adminDeadlineSeconds) { this.adminDeadlineSeconds = adminDeadlineSeconds; }
    public String getAnthropicApiKey() { return anthropicApiKey; }
    public void setAnthropicApiKey(String anthropicApiKey) { this.anthropicApiKey = anthropicApiKey; }
    public String getAnthropicBaseUrl() { return anthropicBaseUrl; }
    public void setAnthropicBaseUrl(String anthropicBaseUrl) { this.anthropicBaseUrl = anthropicBaseUrl; }
    public Map<String, Price> getPrice() { return price; }
    public void setPrice(Map<String, Price> price) { this.price = price; }

    /** Never print the key. */
    @Override
    public String toString() {
        return "ChatProperties[model=" + model + ", maxTokens=" + maxTokens + ", maxToolCalls=" + maxToolCalls
                + ", apiKey=" + (anthropicApiKey == null || anthropicApiKey.isBlank() ? "unset" : "set") + "]";
    }
}
