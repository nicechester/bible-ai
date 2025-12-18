package io.github.nicechester.bibleai.config;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
public class LLMConfig {
    @Value("${langchain4j.llm.gemini.model-name}") private String modelName;
    @Value("${langchain4j.llm.gemini.api-key}") private String apiKey;

    @Bean
    public GoogleAiGeminiChatModel chatModel() {
        log.info("Configuring Gemini ChatModel with model: {}", modelName);
        return GoogleAiGeminiChatModel.builder()
                .modelName(modelName)
                .apiKey(apiKey)
                .maxRetries(0)
                .build();
    }
}

