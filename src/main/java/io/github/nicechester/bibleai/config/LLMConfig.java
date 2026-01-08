package io.github.nicechester.bibleai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.nicechester.bibleai.config.LlamaChatModel;
import io.github.nicechester.bibleai.service.LlamaService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Log4j2
@Configuration
public class LLMConfig {
    @Value("${langchain4j.llm.gemini.model-name:}") private String geminiModelName;
    @Value("${langchain4j.llm.gemini.api-key:}") private String geminiApiKey;
    @Value("${llm.model.path:}") private String llamaModelPath;
    @Value("${llm.model.ngpu:0}") private int llamaNgpuLayers;
    @Value("${llm.model.temperature:0.7}") private float llamaTemperature;
    @Value("${langchain4j.llm.openai.url:}") private String openaiUrl;
    @Value("${langchain4j.llm.openai.model-name:}") private String openaiModelName;
    @Value("${langchain4j.llm.openai.api-key:}") private String openaiApiKey;
    
    @Autowired(required = false)
    private LlamaService llamaService;
    
    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    @ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "llama")
    public LlamaService llamaService() {
        log.info("Creating LlamaService bean (provider is set to llama)");
        return new LlamaService(llamaModelPath, llamaNgpuLayers, llamaTemperature, resourceLoader);
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
    public ChatModel llamaChatModel(LlamaService llamaService) {
        if (llamaService == null || !llamaService.isAvailable()) {
            throw new IllegalStateException(
                "Llama provider selected but model is not available. " +
                "Please configure llm.model.path or set LLM_PROVIDER=gemini to use Gemini instead."
            );
        }
        log.info("Creating Llama ChatModel");
        return new LlamaChatModel(llamaService);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "langchain4j.llm.provider", havingValue = "openai")
    public OpenAiChatModel openAiChatModel() {
        log.debug("Creating OpenAI ChatModel with model: {}", openaiModelName);
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

