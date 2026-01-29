package io.github.nicechester.bibleai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.github.nicechester.bibleai.model.ContextResult;
import io.github.nicechester.bibleai.model.ContextResult.ContextType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextClassifierServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private ContextClassifierService classifierService;

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
        
        classifierService = new ContextClassifierService(embeddingModel);
        classifierService.initializePrototypes();
    }

    @Test
    void testExtract_NullQuery() {
        // When
        ContextResult result = classifierService.extract(null);

        // Then
        assertNotNull(result);
        assertEquals(ContextType.NONE, result.contextType());
        assertFalse(result.hasContext());
    }

    @Test
    void testExtract_EmptyQuery() {
        // When
        ContextResult result = classifierService.extract("   ");

        // Then
        assertNotNull(result);
        assertEquals(ContextType.NONE, result.contextType());
    }

    @Test
    void testExtract_NoContext() {
        // When
        ContextResult result = classifierService.extract("사랑에 대한 말씀");

        // Then
        assertNotNull(result);
        // Without context constraint, should return NONE or the query as cleaned query
        assertNotNull(result.getSearchQuery());
    }

    @Test
    void testNoContext_Factory() {
        // When
        ContextResult result = ContextResult.noContext("test query");

        // Then
        assertNotNull(result);
        assertEquals(ContextType.NONE, result.contextType());
        assertEquals("test query", result.originalQuery());
        assertEquals("test query", result.cleanedQuery());
        assertNull(result.bookShorts());
        assertNull(result.testament());
        assertFalse(result.hasContext());
    }

    @Test
    void testMatchesVerse_NoContext() {
        // Given
        ContextResult noContext = ContextResult.noContext("query");

        // Then
        assertTrue(noContext.matchesVerse("마", 2));
        assertTrue(noContext.matchesVerse("창", 1));
        assertTrue(noContext.matchesVerse(null, null));
    }

    @Test
    void testMatchesVerse_TestamentFilter() {
        // Given - New Testament filter
        ContextResult ntContext = new ContextResult(
            ContextType.TESTAMENT, null, 2, "query", "original", "신약", 1.0);

        // Then
        assertTrue(ntContext.matchesVerse("마", 2)); // NT book
        assertFalse(ntContext.matchesVerse("창", 1)); // OT book
    }

    @Test
    void testMatchesVerse_BookFilter() {
        // Given - Specific book filter
        ContextResult bookContext = new ContextResult(
            ContextType.SINGLE_BOOK, List.of("롬"), null, "query", "original", "로마서", 1.0);

        // Then
        assertTrue(bookContext.matchesVerse("롬", 2));
        assertFalse(bookContext.matchesVerse("고전", 2));
    }

    @Test
    void testMatchesVerse_BookGroupFilter() {
        // Given - Book group filter (Four Gospels)
        ContextResult groupContext = new ContextResult(
            ContextType.BOOK_GROUP, List.of("마", "막", "눅", "요"), null, 
            "query", "original", "사복음서", 1.0);

        // Then
        assertTrue(groupContext.matchesVerse("마", 2));
        assertTrue(groupContext.matchesVerse("막", 2));
        assertTrue(groupContext.matchesVerse("눅", 2));
        assertTrue(groupContext.matchesVerse("요", 2));
        assertFalse(groupContext.matchesVerse("롬", 2));
    }

    @Test
    void testGetSearchQuery_WithCleanedQuery() {
        // Given
        ContextResult result = new ContextResult(
            ContextType.BOOK_GROUP, List.of("마", "막", "눅", "요"), null,
            "사랑에 대한 말씀", "사복음서에서 사랑에 대한 말씀", "사복음서", 1.0);

        // Then
        assertEquals("사랑에 대한 말씀", result.getSearchQuery());
    }

    @Test
    void testGetSearchQuery_FallbackToOriginal() {
        // Given
        ContextResult result = new ContextResult(
            ContextType.NONE, null, null, "", "original query", null, 1.0);

        // Then
        assertEquals("original query", result.getSearchQuery());
    }

    @Test
    void testGetStats() {
        // When
        String stats = classifierService.getStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.contains("ContextClassifier"));
        assertTrue(stats.contains("context prototypes"));
        assertTrue(stats.contains("no-context prototypes"));
    }
}
