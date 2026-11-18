package com.shiyuan.chat.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;

import com.shiyuan.chat.ModelUnavailableException;

class SpringAiModelsTest {

    @Test
    void blankKey_contextStarts_modelThrows() {
        SpringAiModels models = new SpringAiModels("", null, "claude-sonnet-5-5", 2048);
        assertThatThrownBy(models::get)
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessage("Claude API key is not configured");
        assertThatThrownBy(new SpringAiModels(null, null, "claude-sonnet-5-5", 2048)::get)
                .isInstanceOf(ModelUnavailableException.class);
    }

    @Test
    void key_buildsAnthropicModel_withToolExecutionOff() {
        SpringAiModels models = new SpringAiModels("test-key", "http://127.0.0.1:1", "claude-sonnet-5-5", 2048);
        ChatModel model = models.get();
        assertThat(model).isInstanceOf(AnthropicChatModel.class);
        assertThat(model.getDefaultOptions()).isInstanceOf(AnthropicChatOptions.class);
        AnthropicChatOptions opts = (AnthropicChatOptions) model.getDefaultOptions();
        assertThat(opts.getModel()).isEqualTo("claude-sonnet-5-5");
        assertThat(opts.getMaxTokens()).isEqualTo(2048);
        assertThat(opts.getInternalToolExecutionEnabled()).isFalse();
        assertThat(models.get()).isSameAs(model);
    }
}
