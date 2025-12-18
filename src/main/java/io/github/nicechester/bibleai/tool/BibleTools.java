package io.github.nicechester.bibleai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.nicechester.bibleai.service.BibleService;
import io.github.nicechester.bibleai.service.BibleService.VerseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class BibleTools {
    
    private final BibleService bibleService;
    private final ObjectMapper objectMapper;
    @Qualifier("bibleEmbeddingStore")
    private final EmbeddingStore<TextSegment> bibleEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    
    @Tool("Get a specific Bible verse by book name, chapter, and verse number. Returns the verse text with reference (e.g., '창세기 1:1').")
    public String getVerse(String bookName, int chapter, int verse) {
        log.info("Getting verse: {} {}:{}", bookName, chapter, verse);
        try {
            VerseResult result = bibleService.getVerse(bookName, chapter, verse);
            if (result == null) {
                return String.format("Verse not found: %s %d:%d", bookName, chapter, verse);
            }
            return formatVerseResult(result);
        } catch (Exception e) {
            log.error("Failed to get verse", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Get all verses in a chapter. Returns all verses in the specified chapter with their references.")
    public String getChapter(String bookName, int chapter) {
        log.info("Getting chapter: {} {}", bookName, chapter);
        try {
            List<VerseResult> results = bibleService.getChapter(bookName, chapter);
            if (results.isEmpty()) {
                return String.format("Chapter not found: %s %d", bookName, chapter);
            }
            return results.stream()
                .map(this::formatVerseResult)
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Failed to get chapter", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Search for Bible verses containing a keyword. Returns matching verses with their references. Use this when user asks about a specific topic or word.")
    public String searchVerses(String keyword) {
        log.info("Searching verses for keyword: {}", keyword);
        try {
            List<VerseResult> results = bibleService.searchVerses(keyword);
            if (results.isEmpty()) {
                return String.format("No verses found containing: %s", keyword);
            }
            // Limit to first 10 results to avoid overwhelming response
            return results.stream()
                .limit(10)
                .map(this::formatVerseResult)
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Failed to search verses", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Search for Bible verses by phrase (exact or partial match). Returns matching verses with their references. Use this when user asks about a specific phrase or sentence.")
    public String searchByPhrase(String phrase) {
        log.info("Searching verses for phrase: {}", phrase);
        try {
            List<VerseResult> results = bibleService.searchByPhrase(phrase);
            if (results.isEmpty()) {
                return String.format("No verses found containing phrase: %s", phrase);
            }
            // Limit to first 10 results
            return results.stream()
                .limit(10)
                .map(this::formatVerseResult)
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Failed to search by phrase", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Get verses in a range (e.g., 창세기 1:1-10). Returns all verses from startVerse to endVerse in the specified chapter.")
    public String getVerseRange(String bookName, int chapter, int startVerse, int endVerse) {
        log.info("Getting verse range: {} {}:{}-{}", bookName, chapter, startVerse, endVerse);
        try {
            List<VerseResult> results = bibleService.getVerseRange(bookName, chapter, startVerse, endVerse);
            if (results.isEmpty()) {
                return String.format("No verses found: %s %d:%d-%d", bookName, chapter, startVerse, endVerse);
            }
            return results.stream()
                .map(this::formatVerseResult)
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Failed to get verse range", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Get a verse with surrounding context. Returns the specified verse plus a few verses before and after it for better understanding.")
    public String getVerseWithContext(String bookName, int chapter, int verse, int contextVerses) {
        log.info("Getting verse with context: {} {}:{} (context: {} verses)", bookName, chapter, verse, contextVerses);
        try {
            List<VerseResult> results = bibleService.getVerseWithContext(bookName, chapter, verse, contextVerses);
            if (results.isEmpty()) {
                return String.format("Verse not found: %s %d:%d", bookName, chapter, verse);
            }
            return results.stream()
                .map(this::formatVerseResult)
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Failed to get verse with context", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Get statistics about a keyword: how many times it appears, in which books, and sample references. " +
          "Use this when user asks about frequency or distribution of a word or topic. " +
          "Optional filters: testament (1=구약, 2=신약, null=all) and bookType (e.g., '선지서', '복음서', '서신서', null=all). " +
          "If user mentions '구약' or 'Old Testament', use testament=1. If user mentions '신약' or 'New Testament', use testament=2. " +
          "If user mentions book type like '선지서', use bookType='선지서'. You can combine filters.")
    public String getKeywordStatistics(String keyword, Integer testament, String bookType) {
        log.info("Getting keyword statistics: {} (testament: {}, bookType: {})", keyword, testament, bookType);
        try {
            Map<String, Object> stats = bibleService.getKeywordStatistics(keyword, testament, bookType);
            StringBuilder sb = new StringBuilder();
            sb.append("Keyword: ").append(stats.get("keyword")).append("\n");
            sb.append("Total occurrences: ").append(stats.get("totalOccurrences")).append("\n");
            sb.append("Found in ").append(stats.get("booksWithKeyword")).append(" book(s)\n\n");
            
            @SuppressWarnings("unchecked")
            Map<String, Integer> bookCounts = (Map<String, Integer>) stats.get("bookCounts");
            if (bookCounts != null && !bookCounts.isEmpty()) {
                sb.append("Occurrences by book:\n");
                bookCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> sb.append("  - ").append(entry.getKey())
                        .append(": ").append(entry.getValue()).append(" times\n"));
            }
            
            @SuppressWarnings("unchecked")
            List<String> samples = (List<String>) stats.get("sampleReferences");
            if (samples != null && !samples.isEmpty()) {
                sb.append("\nSample references:\n");
                samples.forEach(ref -> sb.append("  - ").append(ref).append("\n"));
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to get keyword statistics", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Get all books in the Bible. Returns a list of all book names. Use this to help identify book names when user mentions them.")
    public String getAllBooks() {
        log.info("Getting all books");
        try {
            return bibleService.getAllBooks().stream()
                .map(book -> String.format("%s (%s)", book.getBookName(), book.getBookShort()))
                .collect(Collectors.joining(", "));
        } catch (Exception e) {
            log.error("Failed to get all books", e);
            return "Error: " + e.getMessage();
        }
    }
    
    @Tool("Search Bible verses using semantic similarity (embedding search). " +
          "Use this when you need to find verses that are semantically related to a topic, even if they don't contain the exact keyword. " +
          "This is useful for finding verses about concepts, themes, or ideas. " +
          "Note: The embedding model has limitations with Korean text, so always verify results are from correct books. " +
          "If results are from wrong books (e.g., 구약 when asking about 신약), ignore them and use other search tools instead.")
    public String searchVersesBySemanticSimilarity(String query, int maxResults) {
        log.info("Searching verses by semantic similarity: {} (maxResults: {})", query, maxResults);
        try {
            // Create embedding for the query
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            
            // Search embedding store
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(0.5)  // Lower threshold for Korean text
                    .build();
            
            List<TextSegment> matches = bibleEmbeddingStore.search(searchRequest).matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .toList();
            
            if (matches.isEmpty()) {
                return String.format("No semantically similar verses found for: %s", query);
            }
            
            // Format results
            StringBuilder sb = new StringBuilder();
            sb.append("Semantically similar verses for '").append(query).append("':\n\n");
            for (TextSegment segment : matches) {
                sb.append(segment.text()).append("\n\n");
            }
            
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to search verses by semantic similarity", e);
            return "Error: " + e.getMessage();
        }
    }
    
    private String formatVerseResult(VerseResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.getReference());
        if (result.getTitle() != null && !result.getTitle().isEmpty()) {
            sb.append(" <").append(result.getTitle()).append(">");
        }
        sb.append("\n").append(result.getText());
        return sb.toString();
    }
}

