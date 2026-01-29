package io.github.nicechester.bibleai.agent;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import io.github.nicechester.bibleai.model.SearchIntent;
import io.github.nicechester.bibleai.model.SearchResponse;
import io.github.nicechester.bibleai.model.VerseResult;
import io.github.nicechester.bibleai.service.*;
import io.github.nicechester.bibleai.service.ResponseIntentClassifier.ResponseType;
import io.github.nicechester.bibleai.tool.BibleTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class BibleAgent {
    private final BibleTools bibleTools;
    private final SessionMemoryManager sessionMemoryManager;
    private final ChatModel chatModel;
    private final SmartBibleSearchService smartSearchService;
    private final ResponseIntentClassifier responseIntentClassifier;
    private final BibleService bibleService;

    public interface BibleAssistant {
        String chat(@UserMessage String userMessage);
    }
    
    /**
     * Handle a user query with session-based conversation history.
     * Uses dual intent classification:
     * 1. Search intent (KEYWORD/SEMANTIC/HYBRID) - handled by SmartBibleSearchService
     * 2. Response intent (DIAGRAM/EXPLANATION/LIST/STATISTICS) - handled by ResponseIntentClassifier
     * 
     * @param userQuery The natural language query from the user
     * @param sessionId Optional session ID for maintaining conversation context
     * @return The assistant's response
     */
    public String handleQuery(String userQuery, String sessionId) {
        ChatMemory sessionMemory = null;
        try {
            // Get or create session-specific memory
            sessionMemory = sessionMemoryManager.getOrCreateMemory(sessionId);
            
            // Step 1: Classify response intent (how to present the response)
            ResponseType responseType = responseIntentClassifier.classify(userQuery);
            log.info("Response intent classified: {} for query '{}'", responseType, userQuery);
            
            // Step 2: Execute smart search (always - provides context)
            SearchResponse searchResults = smartSearchService.search(userQuery, 
                responseType == ResponseType.DIAGRAM ? 20 : 10,  // More results for diagrams
                0.3,
                null);
            
            log.info("Smart search returned {} results (method: {}, context: {})",
                searchResults.getTotalResults(),
                searchResults.getSearchMethod(),
                searchResults.getDetectedContext() != null ? searchResults.getDetectedContext() : "none");
            
            // Step 3: Route based on response type
            if (!responseIntentClassifier.requiresLLM(responseType)) {
                // Direct response without LLM
                return formatDirectResponse(searchResults, responseType);
            }
            
            // Step 4: Invoke LLM with pre-retrieved context
            return invokeWithContext(userQuery, searchResults, responseType, sessionMemory, sessionId);
            
        } catch (Exception e) {
            log.error("Failed to handle query for sessionId: {}", sessionId, e);
            
            // Clear corrupted session memory to prevent propagating the error
            if (sessionId != null && !sessionId.isEmpty()) {
                log.warn("Clearing potentially corrupted session memory for sessionId: {}", sessionId);
                sessionMemoryManager.clearSession(sessionId);
            }
            
            return "Error processing your request: " + e.getMessage() + 
                   "\n\nYour conversation history has been reset to prevent further issues.";
        }
    }
    
    /**
     * Format a direct response without using LLM.
     */
    private String formatDirectResponse(SearchResponse searchResults, ResponseType responseType) {
        if (!searchResults.isSuccess() || searchResults.getResults().isEmpty()) {
            return "검색 결과가 없습니다. 다른 키워드로 검색해 보세요.";
        }
        
        StringBuilder sb = new StringBuilder();
        
        if (responseType == ResponseType.STATISTICS) {
            // Format as statistics
            sb.append("**검색 결과 통계**\n\n");
            sb.append(String.format("- 검색어: %s\n", searchResults.getQuery()));
            sb.append(String.format("- 검색 방법: %s\n", searchResults.getSearchMethod()));
            sb.append(String.format("- 검색 결과: %d건\n", searchResults.getTotalResults()));
            sb.append(String.format("- 검색 시간: %dms\n", searchResults.getSearchTimeMs()));
            
            if (searchResults.getDetectedContext() != null) {
                sb.append(String.format("- 검색 범위: %s\n", searchResults.getDetectedContext()));
            }
            
            sb.append("\n**샘플 구절:**\n");
            searchResults.getResults().stream()
                .limit(5)
                .forEach(v -> sb.append(String.format("- %s: %s\n", v.getReference(), truncate(v.getText(), 50))));
                
        } else {
            // Format as list
            sb.append(String.format("**'%s' 검색 결과 (%d건)**\n\n", 
                searchResults.getQuery(), searchResults.getTotalResults()));
            
            if (searchResults.getDetectedContext() != null) {
                sb.append(String.format("_검색 범위: %s_\n\n", searchResults.getDetectedContext()));
            }
            
            for (VerseResult v : searchResults.getResults()) {
                sb.append(String.format("**%s**", v.getReference()));
                if (v.getTitle() != null && !v.getTitle().isEmpty()) {
                    sb.append(String.format(" <%s>", v.getTitle()));
                }
                sb.append("\n");
                sb.append(v.getText()).append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Invoke LLM with pre-retrieved verse context.
     */
    private String invokeWithContext(String userQuery, SearchResponse searchResults, 
                                     ResponseType responseType, ChatMemory sessionMemory, String sessionId) {
        
        // Build verse context from search results
        String verseContext = searchResults.getResults().stream()
            .map(v -> String.format("%s: %s", v.getReference(), v.getText()))
            .collect(Collectors.joining("\n"));
        
        // Build simplified system prompt based on response type
        String systemMessage = buildSystemMessage(responseType, searchResults);
        
        int messageCount = sessionMemory.messages().size();
        log.info("Processing query for sessionId: {} (memory messages: {})", 
            sessionId != null ? sessionId : "ephemeral", messageCount);
        
        // Clear memory if getting too large (Gemini API constraint)
        if (messageCount >= 8) {
            log.warn("Session memory is getting large ({} messages). Clearing to prevent API issues.", messageCount);
            if (sessionId != null && !sessionId.isEmpty()) {
                sessionMemoryManager.clearSession(sessionId);
                sessionMemory = sessionMemoryManager.getOrCreateMemory(sessionId);
            }
        }
        
        // Build enhanced query with pre-retrieved context
        String enhancedQuery = buildEnhancedQuery(userQuery, verseContext, searchResults);
        
        BibleAssistant assistant = AiServices.builder(BibleAssistant.class)
                .chatModel(chatModel)
                .chatMemory(sessionMemory)
                .tools(bibleTools)
                .systemMessageProvider(chatId -> systemMessage)
                .build();
        
        return assistant.chat(enhancedQuery);
    }
    
    /**
     * Build system message based on response type.
     */
    private String buildSystemMessage(ResponseType responseType, SearchResponse searchResults) {
        String base = """
            You are a Bible study assistant for the Korean Revised Version (개역개정) Bible.
            
            IMPORTANT: Relevant verses have been PRE-RETRIEVED and provided in the user's message.
            These verses are already filtered by search intent and book context.
            Use these verses as your primary source - they are accurate and relevant.
            
            Available tools for additional exploration:
            - getVerse, getChapter, getVerseRange, getVerseWithContext
            - searchVerses (keyword search)
            - advancedBibleSearch (context-aware semantic search)
            - getKeywordStatistics (word frequency)
            
            Always cite verse references when quoting (e.g., "창세기 1:1").
            Support questions in Korean and English.
            """;
        
        return switch (responseType) {
            case DIAGRAM -> base + """
                
                RESPONSE FORMAT: Create a Mermaid flowchart to visualize the information.
                
                Mermaid syntax rules:
                - Use: flowchart TD
                - Korean text in double quotes: A["한글텍스트"]
                - Keep node IDs simple: A, B, C (not Korean)
                - Max 20-30 nodes per diagram
                
                Example:
                ```mermaid
                flowchart TD
                    A["아브라함"] --> B["이삭"]
                    B --> C["야곱"]
                ```
                
                Include a brief text explanation along with the diagram.
                """;
                
            case EXPLANATION -> base + """
                
                RESPONSE FORMAT: Provide a detailed explanation.
                - Explain the historical/theological context
                - Reference specific verses with citations
                - Use clear, educational language
                """;
                
            case CONTEXT -> base + """
                
                RESPONSE FORMAT: Show surrounding context.
                - Use getVerseWithContext to fetch surrounding verses
                - Explain how the context affects interpretation
                """;
                
            default -> base;
        };
    }
    
    /**
     * Build enhanced query with pre-retrieved verse context.
     */
    private String buildEnhancedQuery(String userQuery, String verseContext, SearchResponse searchResults) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[PRE-RETRIEVED VERSES - Use these as your primary source]\n");
        
        if (searchResults.getDetectedContext() != null) {
            sb.append(String.format("Search scope: %s\n", searchResults.getDetectedContext()));
        }
        if (searchResults.getSearchMethod() != null) {
            sb.append(String.format("Search method: %s\n", searchResults.getSearchMethod()));
        }
        
        sb.append("\n");
        sb.append(verseContext);
        sb.append("\n\n[USER QUESTION]\n");
        sb.append(userQuery);
        
        return sb.toString();
    }
    
    /**
     * Truncate text to specified length.
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * Backward compatible method without session ID.
     */
    public String handleQuery(String userQuery) {
        return handleQuery(userQuery, null);
    }
    
    /**
     * Get agent statistics.
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "smartSearch", smartSearchService.getStats(),
            "responseClassifier", responseIntentClassifier.getStats()
        );
    }
}
