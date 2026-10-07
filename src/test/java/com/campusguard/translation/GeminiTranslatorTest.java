package com.campusguard.translation;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusguard.moderation.engine.ai.ChatCompletionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiTranslatorTest {

    private static GeminiTranslator answering(String answer) {
        return new GeminiTranslator(new ChatCompletionPort() {
            @Override public String modelName() { return "stub"; }
            @Override public CompletionResult complete(String system, String user) {
                return new CompletionResult(answer, null, null);
            }
        }, new ObjectMapper());
    }

    @Test
    void readsAFencedAnswerAndDropsKeysItWasNotAsked() {
        var result = answering("```json\n{\"a\":\"你好\",\"x\":\"extra\"}\n```")
                .translate(Map.of("a", "Hello"), "zh-CN");
        assertThat(result).containsExactly(Map.entry("a", "你好"));
    }

    @Test
    void anAnswerThatIsNotJsonTranslatesNothing() {
        assertThat(answering("Sure! Here is the translation: 你好").translate(Map.of("a", "Hello"), "zh-CN")).isEmpty();
        assertThat(answering("{\"a\":\" \"}").translate(Map.of("a", "Hello"), "zh-CN")).isEmpty();
    }
}
