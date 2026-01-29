package io.github.nicechester.bibleai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.nicechester.bibleai.model.ContextResult;
import io.github.nicechester.bibleai.model.SearchIntent;
import io.github.nicechester.bibleai.model.SearchIntent.IntentType;
import io.github.nicechester.bibleai.model.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBibleSearchServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<TextSegment> bibleEmbeddingStore;

    @Mock
    private BibleService bibleService;

    @Mock
    private IntentClassifierService intentClassifier;

    @Mock
    private ContextClassifierService contextClassifier;

    private SmartBibleSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SmartBibleSearchService(
            embeddingModel, 
            bibleEmbeddingStore, 
            bibleService, 
            intentClassifier, 
            contextClassifier
        );
        
        // Set default config values
        ReflectionTestUtils.setField(searchService, "candidateCount", 50);
        ReflectionTestUtils.setField(searchService, "resultCount", 10);
        ReflectionTestUtils.setField(searchService, "minScore", 0.3);
    }

    @Test
    void testSearch_KeywordIntent() {
        // Given
        String query = "모세가 나오는 구절";
        
        ContextResult context = ContextResult.noContext(query);
        when(contextClassifier.extract(query)).thenReturn(context);
        
        SearchIntent intent = new SearchIntent(IntentType.KEYWORD, "모세", query, "Keyword detected");
        when(intentClassifier.classify(query)).thenReturn(intent);
        
        BibleService.VerseResult verseResult = new BibleService.VerseResult();
        verseResult.setReference("출애굽기 3:1");
        verseResult.setBookName("출애굽기");
        verseResult.setBookShort("출");
        verseResult.setChapter(3);
        verseResult.setVerse(1);
        verseResult.setText("모세가 그의 장인 미디안 제사장 이드로의 양 떼를 치더니");
        
        when(bibleService.searchVerses("모세")).thenReturn(List.of(verseResult));
        
        BibleService.Book book = new BibleService.Book();
        book.setBookShort("출");
        book.setTestament(1);
        when(bibleService.findBook(anyString())).thenReturn(book);

        // When
        SearchResponse response = searchService.search(query, 10, 0.3, null);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("KEYWORD", response.getSearchMethod());
        assertEquals("모세", response.getExtractedKeyword());
        verify(bibleService).searchVerses("모세");
    }

    @Test
    void testSearch_SemanticIntent() {
        // Given
        String query = "사랑에 대한 말씀";
        
        ContextResult context = ContextResult.noContext(query);
        when(contextClassifier.extract(query)).thenReturn(context);
        
        SearchIntent intent = new SearchIntent(IntentType.SEMANTIC, null, query, "Semantic detected");
        when(intentClassifier.classify(query)).thenReturn(intent);
        
        // Mock embedding response
        float[] mockVector = new float[384];
        Embedding mockEmbedding = Embedding.from(mockVector);
        when(embeddingModel.embed(query)).thenReturn(Response.from(mockEmbedding));
        
        // Mock search results
        TextSegment segment = TextSegment.from("[KRV] 요한일서 4:8 하나님은 사랑이시라");
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.8, "id1", mockEmbedding, segment);
        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(List.of(match));
        when(bibleEmbeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
        
        BibleService.Book book = new BibleService.Book();
        book.setBookName("요한일서");
        book.setBookShort("요일");
        book.setTestament(2);
        when(bibleService.findBook(anyString())).thenReturn(book);

        // When
        SearchResponse response = searchService.search(query, 10, 0.3, null);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("SEMANTIC", response.getSearchMethod());
        verify(bibleEmbeddingStore).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void testSearch_WithContextFilter() {
        // Given
        String query = "신약에서 사랑에 대한 말씀";
        
        ContextResult context = new ContextResult(
            ContextResult.ContextType.TESTAMENT, null, 2, 
            "사랑에 대한 말씀", query, "신약", 1.0);
        when(contextClassifier.extract(query)).thenReturn(context);
        
        SearchIntent intent = new SearchIntent(IntentType.SEMANTIC, null, "사랑에 대한 말씀", "Semantic");
        when(intentClassifier.classify("사랑에 대한 말씀")).thenReturn(intent);
        
        // Mock embedding
        float[] mockVector = new float[384];
        Embedding mockEmbedding = Embedding.from(mockVector);
        when(embeddingModel.embed("사랑에 대한 말씀")).thenReturn(Response.from(mockEmbedding));
        
        // Mock empty search results
        EmbeddingSearchResult<TextSegment> emptyResult = new EmbeddingSearchResult<>(List.of());
        when(bibleEmbeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(emptyResult);

        // When
        SearchResponse response = searchService.search(query, 10, 0.3, null);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("신약", response.getDetectedContext());
        assertEquals("TESTAMENT", response.getDetectedContextType());
    }

    @Test
    void testSearch_HybridIntent() {
        // Given
        String query = "다윗 왕의 이야기";
        
        ContextResult context = ContextResult.noContext(query);
        when(contextClassifier.extract(query)).thenReturn(context);
        
        SearchIntent intent = new SearchIntent(IntentType.HYBRID, "다윗", query, "Hybrid detected");
        when(intentClassifier.classify(query)).thenReturn(intent);
        
        // Mock keyword search
        BibleService.VerseResult keywordResult = new BibleService.VerseResult();
        keywordResult.setReference("삼상 16:1");
        keywordResult.setBookName("사무엘상");
        keywordResult.setBookShort("삼상");
        keywordResult.setChapter(16);
        keywordResult.setVerse(1);
        keywordResult.setText("여호와께서 사무엘에게 다윗을 왕으로 세우라 하셨다");
        when(bibleService.searchVerses("다윗")).thenReturn(List.of(keywordResult));
        
        // Mock embedding
        float[] mockVector = new float[384];
        Embedding mockEmbedding = Embedding.from(mockVector);
        when(embeddingModel.embed(query)).thenReturn(Response.from(mockEmbedding));
        
        // Mock semantic search
        EmbeddingSearchResult<TextSegment> emptyResult = new EmbeddingSearchResult<>(List.of());
        when(bibleEmbeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(emptyResult);
        
        BibleService.Book book = new BibleService.Book();
        book.setBookShort("삼상");
        book.setTestament(1);
        when(bibleService.findBook(anyString())).thenReturn(book);

        // When
        SearchResponse response = searchService.search(query, 10, 0.3, null);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("HYBRID", response.getSearchMethod());
    }

    @Test
    void testSearch_Exception() {
        // Given
        String query = "error test";
        when(contextClassifier.extract(query)).thenThrow(new RuntimeException("Test error"));

        // When
        SearchResponse response = searchService.search(query, 10, 0.3, null);

        // Then
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
        assertTrue(response.getError().contains("Test error"));
    }

    @Test
    void testGetStats() {
        // Given
        when(intentClassifier.getStats()).thenReturn("IntentClassifier stats");
        when(contextClassifier.getStats()).thenReturn("ContextClassifier stats");

        // When
        Map<String, Object> stats = searchService.getStats();

        // Then
        assertNotNull(stats);
        assertEquals(50, stats.get("candidateCount"));
        assertEquals(10, stats.get("resultCount"));
        assertEquals(0.3, stats.get("minScore"));
        assertEquals("IntentClassifier stats", stats.get("intentClassifier"));
        assertEquals("ContextClassifier stats", stats.get("contextClassifier"));
    }
}
