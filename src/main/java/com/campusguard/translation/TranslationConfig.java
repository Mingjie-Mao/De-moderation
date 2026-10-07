package com.campusguard.translation;

import com.campusguard.moderation.engine.ai.ModelPolicies;
import com.campusguard.moderation.engine.ai.ResilientChatCompletion;
import com.campusguard.moderation.engine.ai.SpringAiChatCompletion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The translator exists only when a model does, and calls the model live
 * moderation uses, behind its circuit: a throttled provider then fails
 * translations fast instead of letting them compete with the queue.
 */
@Configuration
@EnableConfigurationProperties(TranslationProperties.class)
public class TranslationConfig {

    /** Best-effort reading aid: one worker and eight queued batches, never a request thread. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService translationExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), runnable -> {
                    Thread thread = new Thread(runnable, "content-translation");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
    public Translator geminiTranslator(ChatModel chatModel, ModelPolicies policies, ObjectMapper objectMapper) {
        String model = policies.primaryModel();
        return new GeminiTranslator(
                new ResilientChatCompletion(new SpringAiChatCompletion(chatModel, model), policies.forModel(model)),
                objectMapper);
    }
}
