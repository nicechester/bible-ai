package io.github.nicechester.bibleai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Map;

@Log4j2
@Configuration
public class LLMConfig {
    @Value("${langchain4j.llm.gemini.model-name:}") private String geminiModelName;
    @Value("${langchain4j.llm.gemini.api-key:}") private String geminiApiKey;
    @Value("${langchain4j.llm.openai.url:}") private String openaiUrl;
    @Value("${langchain4j.llm.openai.model-name:}") private String openaiModelName;
    @Value("${langchain4j.llm.openai.api-key:}") private String openaiApiKey;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "gemini", matchIfMissing = true)
    public ChatModel geminiChatModel() {
        log.info("Creating Gemini ChatModel with model: {}", geminiModelName);
        return GoogleAiGeminiChatModel.builder()
                .modelName(geminiModelName)
                .apiKey(geminiApiKey)
                .maxRetries(0)
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "openai")
    public OpenAiChatModel openAiChatModel() {
        log.info("Creating OpenAI ChatModel with model: {}", openaiModelName);
        return OpenAiChatModel.builder()
                .baseUrl(openaiUrl)
                .customHeaders(Map.of(
                        "Authorization", "Bearer " + openaiApiKey,
                        "X-Api-Key", openaiApiKey))
                .apiKey(openaiApiKey)
                .modelName(openaiModelName)
                .maxRetries(0)
                .timeout(Duration.ofMinutes(10))
                .build();
    }
}

