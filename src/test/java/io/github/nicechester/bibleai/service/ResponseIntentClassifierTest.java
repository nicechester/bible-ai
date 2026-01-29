package io.github.nicechester.bibleai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.nicechester.bibleai.service.ResponseIntentClassifier.ResponseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseIntentClassifierTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private ResponseIntentClassifier classifierService;

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
        
        classifierService = new ResponseIntentClassifier(embeddingModel);
        classifierService.initializePrototypes();
    }

    @Test
    void testClassify_NullQuery() {
        // When
        ResponseType result = classifierService.classify(null);

        // Then
        assertEquals(ResponseType.LIST, result);
    }

    @Test
    void testClassify_EmptyQuery() {
        // When
        ResponseType result = classifierService.classify("   ");

        // Then
        assertEquals(ResponseType.LIST, result);
    }

    @Test
    void testClassify_ReturnsValidType() {
        // When
        ResponseType result = classifierService.classify("사랑에 대한 구절을 찾아줘");

        // Then
        assertNotNull(result);
        assertTrue(result == ResponseType.LIST || 
                   result == ResponseType.EXPLANATION ||
                   result == ResponseType.DIAGRAM ||
                   result == ResponseType.STATISTICS ||
                   result == ResponseType.CONTEXT ||
                   result == ResponseType.DIRECT);
    }

    @Test
    void testRequiresLLM_Diagram() {
        // Then
        assertTrue(classifierService.requiresLLM(ResponseType.DIAGRAM));
    }

    @Test
    void testRequiresLLM_Explanation() {
        // Then
        assertTrue(classifierService.requiresLLM(ResponseType.EXPLANATION));
    }

    @Test
    void testRequiresLLM_Context() {
        // Then
        assertTrue(classifierService.requiresLLM(ResponseType.CONTEXT));
    }

    @Test
    void testRequiresLLM_Statistics() {
        // Then - Statistics can be answered directly
        assertFalse(classifierService.requiresLLM(ResponseType.STATISTICS));
    }

    @Test
    void testRequiresLLM_List() {
        // Then - Simple list can be returned directly
        assertFalse(classifierService.requiresLLM(ResponseType.LIST));
    }

    @Test
    void testRequiresLLM_Direct() {
        // Then - Direct lookup doesn't need LLM
        assertFalse(classifierService.requiresLLM(ResponseType.DIRECT));
    }

    @Test
    void testGetStats() {
        // When
        String stats = classifierService.getStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.contains("ResponseIntentClassifier"));
        assertTrue(stats.contains("diagram"));
        assertTrue(stats.contains("explanation"));
        assertTrue(stats.contains("statistics"));
    }

    @Test
    void testResponseTypeValues() {
        // Verify all expected response types exist
        ResponseType[] types = ResponseType.values();
        
        assertEquals(6, types.length);
        assertNotNull(ResponseType.valueOf("DIAGRAM"));
        assertNotNull(ResponseType.valueOf("EXPLANATION"));
        assertNotNull(ResponseType.valueOf("LIST"));
        assertNotNull(ResponseType.valueOf("STATISTICS"));
        assertNotNull(ResponseType.valueOf("CONTEXT"));
        assertNotNull(ResponseType.valueOf("DIRECT"));
    }
}
