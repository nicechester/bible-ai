package io.github.nicechester.bibleai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.github.nicechester.bibleai.config.LlamaChatModel;
import io.github.nicechester.bibleai.service.LlamaService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Log4j2
@Configuration
public class LLMConfig {
    @Value("${langchain4j.llm.gemini.model-name:}") private String geminiModelName;
    @Value("${langchain4j.llm.gemini.api-key:}") private String geminiApiKey;
    
    private final LlamaService llamaService;

    public LLMConfig(LlamaService llamaService) {
        this.llamaService = llamaService;
    }

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
    @ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "llama")
    public ChatModel llamaChatModel() {
        if (!llamaService.isAvailable()) {
            throw new IllegalStateException(
                "Llama provider selected but model is not available. " +
                "Please configure llm.model.path or set LLM_PROVIDER=gemini to use Gemini instead."
            );
        }
        log.info("Creating Llama ChatModel");
        return new LlamaChatModel(llamaService);
    }
}

