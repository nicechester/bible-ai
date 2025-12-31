package io.github.nicechester.bibleai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.nicechester.bibleai.service.BibleService;
import io.github.nicechester.bibleai.service.BibleService.Book;
import io.github.nicechester.bibleai.service.BibleService.VerseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BibleToolsTest {

    @Mock
    private BibleService bibleService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    @Qualifier("bibleEmbeddingStore")
    private EmbeddingStore<TextSegment> bibleEmbeddingStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private BibleTools bibleTools;

    private VerseResult sampleVerseResult;

    @BeforeEach
    void setUp() {
        sampleVerseResult = new VerseResult();
        sampleVerseResult.setReference("요한복음 3:16");
        sampleVerseResult.setTitle("하나님의 사랑");
        sampleVerseResult.setText("하나님이 세상을 이처럼 사랑하사 독생자를 주셨으니");
    }

    @Test
    void testGetVerse_Success() {
        // Given
        when(bibleService.getVerse("요한복음", 3, 16)).thenReturn(sampleVerseResult);

        // When
        String result = bibleTools.getVerse("요한복음", 3, 16);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:16"));
        assertTrue(result.contains("하나님의 사랑"));
        assertTrue(result.contains("하나님이 세상을 이처럼 사랑하사 독생자를 주셨으니"));
        verify(bibleService, times(1)).getVerse("요한복음", 3, 16);
    }

    @Test
    void testGetVerse_NotFound() {
        // Given
        when(bibleService.getVerse("요한복음", 3, 16)).thenReturn(null);

        // When
        String result = bibleTools.getVerse("요한복음", 3, 16);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Verse not found"));
        assertTrue(result.contains("요한복음"));
        verify(bibleService, times(1)).getVerse("요한복음", 3, 16);
    }

    @Test
    void testGetVerse_Exception() {
        // Given
        when(bibleService.getVerse("요한복음", 3, 16))
                .thenThrow(new RuntimeException("Database error"));

        // When
        String result = bibleTools.getVerse("요한복음", 3, 16);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Error"));
        verify(bibleService, times(1)).getVerse("요한복음", 3, 16);
    }

    @Test
    void testGetChapter_Success() {
        // Given
        VerseResult verse1 = createVerseResult("요한복음 3:1", "니고데모", "한 사람이 있으니");
        VerseResult verse2 = createVerseResult("요한복음 3:2", null, "밤에 예수께 와서");
        List<VerseResult> verses = Arrays.asList(verse1, verse2);
        when(bibleService.getChapter("요한복음", 3)).thenReturn(verses);

        // When
        String result = bibleTools.getChapter("요한복음", 3);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:1"));
        assertTrue(result.contains("요한복음 3:2"));
        verify(bibleService, times(1)).getChapter("요한복음", 3);
    }

    @Test
    void testGetChapter_NotFound() {
        // Given
        when(bibleService.getChapter("요한복음", 100)).thenReturn(new ArrayList<>());

        // When
        String result = bibleTools.getChapter("요한복음", 100);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Chapter not found"));
        verify(bibleService, times(1)).getChapter("요한복음", 100);
    }

    @Test
    void testSearchVerses_Success() {
        // Given
        VerseResult verse1 = createVerseResult("요한복음 3:16", "하나님의 사랑", "하나님이 세상을 이처럼 사랑하사");
        VerseResult verse2 = createVerseResult("요한일서 4:8", null, "하나님은 사랑이시라");
        List<VerseResult> results = Arrays.asList(verse1, verse2);
        when(bibleService.searchVerses("사랑")).thenReturn(results);

        // When
        String result = bibleTools.searchVerses("사랑");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:16"));
        assertTrue(result.contains("사랑"));
        verify(bibleService, times(1)).searchVerses("사랑");
    }

    @Test
    void testSearchVerses_LimitsTo10() {
        // Given
        List<VerseResult> manyResults = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            manyResults.add(createVerseResult("요한복음 3:" + i, null, "사랑 " + i));
        }
        when(bibleService.searchVerses("사랑")).thenReturn(manyResults);

        // When
        String result = bibleTools.searchVerses("사랑");

        // Then
        assertNotNull(result);
        // Should only contain first 10 results
        assertTrue(result.contains("요한복음 3:1"));
        assertTrue(result.contains("요한복음 3:10"));
        assertFalse(result.contains("요한복음 3:11"));
        verify(bibleService, times(1)).searchVerses("사랑");
    }

    @Test
    void testSearchVerses_NotFound() {
        // Given
        when(bibleService.searchVerses("존재하지않는단어")).thenReturn(new ArrayList<>());

        // When
        String result = bibleTools.searchVerses("존재하지않는단어");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("No verses found"));
        verify(bibleService, times(1)).searchVerses("존재하지않는단어");
    }

    @Test
    void testSearchByPhrase_Success() {
        // Given
        VerseResult verse = createVerseResult("요한복음 3:16", null, "하나님이 세상을 이처럼 사랑하사");
        List<VerseResult> results = Arrays.asList(verse);
        when(bibleService.searchByPhrase("세상을 이처럼")).thenReturn(results);

        // When
        String result = bibleTools.searchByPhrase("세상을 이처럼");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:16"));
        verify(bibleService, times(1)).searchByPhrase("세상을 이처럼");
    }

    @Test
    void testSearchByPhrase_LimitsTo10() {
        // Given
        List<VerseResult> manyResults = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            manyResults.add(createVerseResult("요한복음 3:" + i, null, "phrase " + i));
        }
        when(bibleService.searchByPhrase("phrase")).thenReturn(manyResults);

        // When
        String result = bibleTools.searchByPhrase("phrase");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:10"));
        assertFalse(result.contains("요한복음 3:11"));
        verify(bibleService, times(1)).searchByPhrase("phrase");
    }

    @Test
    void testGetVerseRange_Success() {
        // Given
        List<VerseResult> verses = Arrays.asList(
            createVerseResult("요한복음 3:1", null, "verse 1"),
            createVerseResult("요한복음 3:2", null, "verse 2"),
            createVerseResult("요한복음 3:3", null, "verse 3")
        );
        when(bibleService.getVerseRange("요한복음", 3, 1, 3)).thenReturn(verses);

        // When
        String result = bibleTools.getVerseRange("요한복음", 3, 1, 3);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:1"));
        assertTrue(result.contains("요한복음 3:3"));
        verify(bibleService, times(1)).getVerseRange("요한복음", 3, 1, 3);
    }

    @Test
    void testGetVerseRange_NotFound() {
        // Given
        when(bibleService.getVerseRange("요한복음", 3, 100, 200))
                .thenReturn(new ArrayList<>());

        // When
        String result = bibleTools.getVerseRange("요한복음", 3, 100, 200);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("No verses found"));
        verify(bibleService, times(1)).getVerseRange("요한복음", 3, 100, 200);
    }

    @Test
    void testGetVerseWithContext_Success() {
        // Given
        List<VerseResult> verses = Arrays.asList(
            createVerseResult("요한복음 3:14", null, "context before"),
            createVerseResult("요한복음 3:15", null, "target verse"),
            createVerseResult("요한복음 3:16", null, "context after")
        );
        when(bibleService.getVerseWithContext("요한복음", 3, 15, 1)).thenReturn(verses);

        // When
        String result = bibleTools.getVerseWithContext("요한복음", 3, 15, 1);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:15"));
        verify(bibleService, times(1)).getVerseWithContext("요한복음", 3, 15, 1);
    }

    @Test
    void testGetVerseWithContext_NotFound() {
        // Given
        when(bibleService.getVerseWithContext("요한복음", 3, 1000, 3))
                .thenReturn(new ArrayList<>());

        // When
        String result = bibleTools.getVerseWithContext("요한복음", 3, 1000, 3);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Verse not found"));
        verify(bibleService, times(1)).getVerseWithContext("요한복음", 3, 1000, 3);
    }

    @Test
    void testGetKeywordStatistics_Success() {
        // Given
        Map<String, Object> stats = new HashMap<>();
        stats.put("keyword", "사랑");
        stats.put("totalOccurrences", 250);
        stats.put("booksWithKeyword", 45);
        
        Map<String, Integer> bookCounts = new HashMap<>();
        bookCounts.put("요한복음", 50);
        bookCounts.put("고린도전서", 30);
        bookCounts.put("요한일서", 20);
        stats.put("bookCounts", bookCounts);
        
        List<String> samples = Arrays.asList("요한복음 3:16", "고린도전서 13:4");
        stats.put("sampleReferences", samples);
        
        when(bibleService.getKeywordStatistics("사랑", null, null)).thenReturn(stats);

        // When
        String result = bibleTools.getKeywordStatistics("사랑", null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("사랑"));
        assertTrue(result.contains("250"));
        assertTrue(result.contains("45"));
        assertTrue(result.contains("요한복음"));
        assertTrue(result.contains("요한복음 3:16"));
        verify(bibleService, times(1)).getKeywordStatistics("사랑", null, null);
    }

    @Test
    void testGetKeywordStatistics_WithFilters() {
        // Given
        Map<String, Object> stats = new HashMap<>();
        stats.put("keyword", "사랑");
        stats.put("totalOccurrences", 100);
        stats.put("booksWithKeyword", 20);
        stats.put("bookCounts", new HashMap<String, Integer>());
        stats.put("sampleReferences", new ArrayList<String>());
        
        when(bibleService.getKeywordStatistics("사랑", 2, "복음서")).thenReturn(stats);

        // When
        String result = bibleTools.getKeywordStatistics("사랑", 2, "복음서");

        // Then
        assertNotNull(result);
        assertTrue(result.contains("사랑"));
        verify(bibleService, times(1)).getKeywordStatistics("사랑", 2, "복음서");
    }

    @Test
    void testGetKeywordStatistics_Exception() {
        // Given
        when(bibleService.getKeywordStatistics("사랑", null, null))
                .thenThrow(new RuntimeException("Search error"));

        // When
        String result = bibleTools.getKeywordStatistics("사랑", null, null);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Error"));
        verify(bibleService, times(1)).getKeywordStatistics("사랑", null, null);
    }

    @Test
    void testGetAllBooks_Success() {
        // Given
        Book book1 = new Book();
        book1.setBookName("창세기");
        book1.setBookShort("창");
        
        Book book2 = new Book();
        book2.setBookName("요한복음");
        book2.setBookShort("요");
        
        List<Book> books = Arrays.asList(book1, book2);
        when(bibleService.getAllBooks()).thenReturn(books);

        // When
        String result = bibleTools.getAllBooks();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("창세기"));
        assertTrue(result.contains("요한복음"));
        verify(bibleService, times(1)).getAllBooks();
    }

    @Test
    void testGetAllBooks_Exception() {
        // Given
        when(bibleService.getAllBooks()).thenThrow(new RuntimeException("Load error"));

        // When
        String result = bibleTools.getAllBooks();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Error"));
        verify(bibleService, times(1)).getAllBooks();
    }

    @Test
    void testSearchVersesBySemanticSimilarity_Success() {
        // Given
        Embedding queryEmbedding = mock(Embedding.class);
        Response<Embedding> embeddingResponse = Response.from(queryEmbedding);
        when(embeddingModel.embed("사랑")).thenReturn(embeddingResponse);
        
        TextSegment segment1 = TextSegment.from("요한복음 3:16 하나님의 사랑");
        TextSegment segment2 = TextSegment.from("고린도전서 13:4 사랑은 오래 참고");
        
        Embedding embedding1 = mock(Embedding.class);
        Embedding embedding2 = mock(Embedding.class);
        
        EmbeddingMatch<TextSegment> match1 = new EmbeddingMatch<>(0.8, "id1", embedding1, segment1);
        EmbeddingMatch<TextSegment> match2 = new EmbeddingMatch<>(0.7, "id2", embedding2, segment2);
        
        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(
            Arrays.asList(match1, match2)
        );
        
        when(bibleEmbeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(searchResult);

        // When
        String result = bibleTools.searchVersesBySemanticSimilarity("사랑", 5);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("사랑"));
        assertTrue(result.contains("요한복음 3:16"));
        verify(embeddingModel, times(1)).embed("사랑");
        verify(bibleEmbeddingStore, times(1)).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void testSearchVersesBySemanticSimilarity_NotFound() {
        // Given
        Embedding queryEmbedding = mock(Embedding.class);
        Response<Embedding> embeddingResponse = Response.from(queryEmbedding);
        when(embeddingModel.embed("존재하지않는주제")).thenReturn(embeddingResponse);
        
        EmbeddingSearchResult<TextSegment> emptyResult = new EmbeddingSearchResult<>(
            new ArrayList<>()
        );
        
        when(bibleEmbeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(emptyResult);

        // When
        String result = bibleTools.searchVersesBySemanticSimilarity("존재하지않는주제", 5);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("No semantically similar verses found"));
        verify(embeddingModel, times(1)).embed("존재하지않는주제");
    }

    @Test
    void testSearchVersesBySemanticSimilarity_Exception() {
        // Given
        when(embeddingModel.embed("사랑"))
                .thenThrow(new RuntimeException("Embedding error"));

        // When
        String result = bibleTools.searchVersesBySemanticSimilarity("사랑", 5);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Error"));
    }

    @Test
    void testFormatVerseResult_WithTitle() {
        // Given
        VerseResult verse = createVerseResult("요한복음 3:16", "하나님의 사랑", "하나님이 세상을 이처럼 사랑하사");

        // When - Use reflection to test private method indirectly through getVerse
        when(bibleService.getVerse("요한복음", 3, 16)).thenReturn(verse);
        String result = bibleTools.getVerse("요한복음", 3, 16);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:16"));
        assertTrue(result.contains("<하나님의 사랑>"));
        assertTrue(result.contains("하나님이 세상을 이처럼 사랑하사"));
    }

    @Test
    void testFormatVerseResult_WithoutTitle() {
        // Given
        VerseResult verse = createVerseResult("요한복음 3:17", null, "하나님이 그 아들을 세상에 보내신 것은");

        // When
        when(bibleService.getVerse("요한복음", 3, 17)).thenReturn(verse);
        String result = bibleTools.getVerse("요한복음", 3, 17);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("요한복음 3:17"));
        assertFalse(result.contains("<"));
        assertTrue(result.contains("하나님이 그 아들을 세상에 보내신 것은"));
    }

    // Helper method to create VerseResult
    private VerseResult createVerseResult(String reference, String title, String text) {
        VerseResult result = new VerseResult();
        result.setReference(reference);
        result.setTitle(title);
        result.setText(text);
        return result;
    }
}

