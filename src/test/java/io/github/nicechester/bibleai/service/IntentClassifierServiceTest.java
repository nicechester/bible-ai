package io.github.nicechester.bibleai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.nicechester.bibleai.model.SearchIntent;
import io.github.nicechester.bibleai.model.SearchIntent.IntentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntentClassifierServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private IntentClassifierService classifierService;

    @BeforeEach
    void setUp() {
        // Create mock embedding response
        float[] mockVector = new float[384];
        for (int i = 0; i < 384; i++) {
            mockVector[i] = (float) Math.random();
        }
        Embedding mockEmbedding = Embedding.from(mockVector);
        Response<Embedding> mockResponse = Response.from(mockEmbedding);
        
        when(embeddingModel.embed(anyString())).thenReturn(mockResponse);
        
        classifierService = new IntentClassifierService(embeddingModel);
        classifierService.initializePrototypes();
    }

    @Test
    void testClassify_NullQuery() {
        // When
        SearchIntent result = classifierService.classify(null);

        // Then
        assertNotNull(result);
        assertEquals(IntentType.SEMANTIC, result.type());
        assertTrue(result.reason().contains("Empty query"));
    }

    @Test
    void testClassify_EmptyQuery() {
        // When
        SearchIntent result = classifierService.classify("   ");

        // Then
        assertNotNull(result);
        assertEquals(IntentType.SEMANTIC, result.type());
    }

    @Test
    void testClassify_ShortSingleWord() {
        // When - Single short word should be HYBRID
        SearchIntent result = classifierService.classify("사랑");

        // Then
        assertNotNull(result);
        assertEquals(IntentType.HYBRID, result.type());
        assertEquals("사랑", result.extractedKeyword());
    }

    @Test
    void testClassify_ReturnsSearchIntent() {
        // When
        SearchIntent result = classifierService.classify("모세가 나오는 구절");

        // Then
        assertNotNull(result);
        assertNotNull(result.type());
        assertNotNull(result.originalQuery());
        assertNotNull(result.reason());
        assertEquals("모세가 나오는 구절", result.originalQuery());
    }

    @Test
    void testClassify_PreservesOriginalQuery() {
        // Given
        String query = "하나님의 사랑에 대한 말씀을 찾아줘";

        // When
        SearchIntent result = classifierService.classify(query);

        // Then
        assertEquals(query, result.originalQuery());
    }

    @Test
    void testNeedsKeywordSearch() {
        // Given
        SearchIntent keywordIntent = new SearchIntent(IntentType.KEYWORD, "test", "query", "reason");
        SearchIntent semanticIntent = new SearchIntent(IntentType.SEMANTIC, null, "query", "reason");
        SearchIntent hybridIntent = new SearchIntent(IntentType.HYBRID, "test", "query", "reason");

        // Then
        assertTrue(keywordIntent.needsKeywordSearch());
        assertFalse(semanticIntent.needsKeywordSearch());
        assertTrue(hybridIntent.needsKeywordSearch());
    }

    @Test
    void testNeedsSemanticSearch() {
        // Given
        SearchIntent keywordIntent = new SearchIntent(IntentType.KEYWORD, "test", "query", "reason");
        SearchIntent semanticIntent = new SearchIntent(IntentType.SEMANTIC, null, "query", "reason");
        SearchIntent hybridIntent = new SearchIntent(IntentType.HYBRID, "test", "query", "reason");

        // Then
        assertFalse(keywordIntent.needsSemanticSearch());
        assertTrue(semanticIntent.needsSemanticSearch());
        assertTrue(hybridIntent.needsSemanticSearch());
    }

    @Test
    void testGetStats() {
        // When
        String stats = classifierService.getStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.contains("IntentClassifier"));
        assertTrue(stats.contains("keyword prototypes"));
        assertTrue(stats.contains("semantic prototypes"));
    }
}
