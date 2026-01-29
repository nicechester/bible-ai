package io.github.nicechester.bibleai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.nicechester.bibleai.model.ContextResult;
import io.github.nicechester.bibleai.model.SearchIntent;
import io.github.nicechester.bibleai.model.SearchIntent.IntentType;
import io.github.nicechester.bibleai.model.SearchResponse;
import io.github.nicechester.bibleai.model.VerseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart Bible Search Service implementing Two-Stage Retrieval with Intent Classification.
 * 
 * Combines:
 * - IntentClassifierService for search intent (KEYWORD/SEMANTIC/HYBRID)
 * - ContextClassifierService for scope extraction (book/testament filtering)
 * - Two-stage retrieval (bi-encoder + re-ranking)
 * - Version filtering (KRV/ASV)
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SmartBibleSearchService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> bibleEmbeddingStore;
    private final BibleService bibleService;
    private final IntentClassifierService intentClassifier;
    private final ContextClassifierService contextClassifier;

    @Value("${bible.search.candidate-count:50}")
    private int candidateCount;

    @Value("${bible.search.result-count:10}")
    private int resultCount;

    @Value("${bible.search.min-score:0.3}")
    private double minScore;

    /**
     * Perform search with automatic intent detection and context extraction.
     */
    public SearchResponse search(String query, Integer maxResults, Double minScoreThreshold, String versionFilter) {
        long startTime = System.currentTimeMillis();

        try {
            int resultsToReturn = maxResults != null ? maxResults : resultCount;
            double threshold = minScoreThreshold != null ? minScoreThreshold : minScore;

            // Step 1: Extract context (book scope) from query
            ContextResult context = contextClassifier.extract(query);
            String searchQuery = context.getSearchQuery();
            
            log.info("Context extracted: {} -> '{}' (type: {}, books: {})", 
                     query, searchQuery, context.contextType(), 
                     context.bookShorts() != null ? context.bookShorts() : "all");

            // Step 2: Classify user intent using the cleaned query
            SearchIntent intent = intentClassifier.classify(searchQuery);
            String extractedKeyword = intent.extractedKeyword();
            log.info("Classified intent: {} for query '{}' (keyword: {}) - {}", 
                     intent.type(), searchQuery, extractedKeyword != null ? extractedKeyword : "<query>", intent.reason());

            List<VerseResult> results;

            // Step 3: Perform search based on intent
            String keyword = intent.extractedKeyword();
            if (keyword == null || keyword.isBlank()) {
                keyword = searchQuery;
            }
            
            switch (intent.type()) {
                case KEYWORD:
                    results = performKeywordSearch(keyword, versionFilter, context, resultsToReturn);
                    break;
                case HYBRID:
                    results = performHybridSearch(searchQuery, keyword, threshold, versionFilter, context, resultsToReturn);
                    break;
                case SEMANTIC:
                default:
                    results = performSemanticSearch(searchQuery, threshold, versionFilter, context, resultsToReturn);
                    break;
            }

            long searchTime = System.currentTimeMillis() - startTime;
            log.info("Search completed in {}ms: '{}' [{}] -> {} results (context: {})", 
                     searchTime, query, intent.type(), results.size(), 
                     context.hasContext() ? context.contextDescription() : "none");

            return SearchResponse.success(query, results, searchTime, intent, context);

        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            return SearchResponse.error(query, e.getMessage());
        }
    }

    /**
     * Perform keyword-only search (exact text match).
     */
    private List<VerseResult> performKeywordSearch(String keyword, String versionFilter, 
                                                    ContextResult context, int maxResults) {
        log.debug("Performing keyword search for: {} (context: {})", keyword,
                 context.hasContext() ? context.contextDescription() : "none");
        
        List<BibleService.VerseResult> matches = bibleService.searchVerses(keyword);
        
        return matches.stream()
            .filter(v -> matchesVersion(v, versionFilter))
            .filter(v -> matchesContext(v, context))
            .limit(maxResults)
            .map(v -> convertToVerseResult(v, 1.0, 1.0))
            .collect(Collectors.toList());
    }

    /**
     * Perform hybrid search: combines keyword and semantic results.
     */
    private List<VerseResult> performHybridSearch(String query, String keyword, double threshold, 
                                                   String versionFilter, ContextResult context, int maxResults) {
        log.debug("Performing hybrid search: keyword='{}', query='{}' (context: {})", 
                 keyword, query, context.hasContext() ? context.contextDescription() : "none");
        
        // Get keyword matches first (these are exact matches, high priority)
        List<BibleService.VerseResult> keywordMatches = bibleService.searchVerses(keyword);
        Set<String> keywordMatchKeys = keywordMatches.stream()
            .filter(v -> matchesContext(v, context))
            .map(v -> v.getBookShort() + ":" + v.getChapter() + ":" + v.getVerse())
            .collect(Collectors.toSet());
        
        // Get semantic matches
        List<ScoredVerse> semanticCandidates = retrieveCandidates(query, candidateCount);
        
        // Build result list: keyword matches first, then semantic matches that aren't duplicates
        List<VerseResult> results = new ArrayList<>();
        
        // Add keyword matches with boosted score
        keywordMatches.stream()
            .filter(v -> matchesVersion(v, versionFilter))
            .filter(v -> matchesContext(v, context))
            .limit(maxResults)
            .forEach(v -> results.add(convertToVerseResult(v, 1.0, 1.0)));
        
        // Add semantic matches that aren't already in keyword results
        if (results.size() < maxResults) {
            String[] queryWords = query.toLowerCase().split("\\s+");
            
            semanticCandidates.stream()
                .filter(sv -> !keywordMatchKeys.contains(sv.key))
                .filter(sv -> matchesVersion(sv.bookShort, versionFilter))
                .filter(sv -> context.matchesVerse(sv.bookShort, sv.testament))
                .map(sv -> {
                    double rerankedScore = calculateRerankedScore(sv, queryWords);
                    return new ScoredVerse(sv.key, sv.bookName, sv.bookShort, sv.chapter, sv.verse,
                                          sv.title, sv.text, sv.testament, sv.score, rerankedScore);
                })
                .filter(sv -> sv.rerankedScore >= threshold)
                .sorted((a, b) -> Double.compare(b.rerankedScore, a.rerankedScore))
                .limit(maxResults - results.size())
                .forEach(sv -> results.add(VerseResult.builder()
                    .reference(sv.bookName + " " + sv.chapter + ":" + sv.verse)
                    .bookName(sv.bookName)
                    .bookShort(sv.bookShort)
                    .chapter(sv.chapter)
                    .verse(sv.verse)
                    .title(sv.title)
                    .text(sv.text)
                    .testament(sv.testament)
                    .score(sv.score)
                    .rerankedScore(sv.rerankedScore)
                    .build()));
        }
        
        return results;
    }

    /**
     * Perform semantic-only search (two-stage retrieval).
     */
    private List<VerseResult> performSemanticSearch(String query, double threshold, 
                                                     String versionFilter, ContextResult context, int maxResults) {
        log.debug("Performing semantic search for: {} (context: {})", query,
                 context.hasContext() ? context.contextDescription() : "none");
        
        // Stage 1: Bi-Encoder Candidate Retrieval
        List<ScoredVerse> candidates = retrieveCandidates(query, candidateCount);

        if (candidates.isEmpty()) {
            return List.of();
        }

        // Stage 2: Re-ranking and Filtering
        return rerankAndFilter(candidates, query, threshold, versionFilter, context, maxResults);
    }

    /**
     * Stage 1: Fast candidate retrieval using bi-encoder embeddings.
     */
    private List<ScoredVerse> retrieveCandidates(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(0.1)
            .build();

        List<EmbeddingMatch<TextSegment>> matches = bibleEmbeddingStore.search(searchRequest).matches();

        List<ScoredVerse> candidates = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            String segmentText = match.embedded().text();
            ScoredVerse sv = parseSegmentText(segmentText, match.score());
            if (sv != null) {
                candidates.add(sv);
            }
        }

        return candidates;
    }

    /**
     * Parse segment text to extract verse information.
     * Format: "[KRV] 창세기 1:1 <제목> 본문" or "[ASV] Genesis 1:1 <Title> Text"
     */
    private ScoredVerse parseSegmentText(String text, double score) {
        try {
            // Pattern: [VERSION] BookName Chapter:Verse <Title> Text
            String remaining = text;
            String version = "KRV";
            
            if (remaining.startsWith("[")) {
                int endBracket = remaining.indexOf("]");
                if (endBracket > 0) {
                    version = remaining.substring(1, endBracket);
                    remaining = remaining.substring(endBracket + 1).trim();
                }
            }
            
            // Find chapter:verse pattern
            int colonIdx = remaining.indexOf(":");
            if (colonIdx < 0) return null;
            
            // Find the start of chapter number
            int spaceBeforeChapter = remaining.lastIndexOf(" ", colonIdx);
            if (spaceBeforeChapter < 0) return null;
            
            String bookName = remaining.substring(0, spaceBeforeChapter).trim();
            
            // Parse chapter
            String chapterStr = remaining.substring(spaceBeforeChapter + 1, colonIdx);
            int chapter = Integer.parseInt(chapterStr);
            
            // Parse verse and rest
            int verseEnd = colonIdx + 1;
            while (verseEnd < remaining.length() && Character.isDigit(remaining.charAt(verseEnd))) {
                verseEnd++;
            }
            int verse = Integer.parseInt(remaining.substring(colonIdx + 1, verseEnd));
            
            String rest = remaining.substring(verseEnd).trim();
            
            // Extract title if present
            String title = null;
            String verseText = rest;
            if (rest.startsWith("<")) {
                int titleEnd = rest.indexOf(">");
                if (titleEnd > 0) {
                    title = rest.substring(1, titleEnd);
                    verseText = rest.substring(titleEnd + 1).trim();
                }
            }
            
            // Find book info
            BibleService.Book book = bibleService.findBook(bookName);
            String bookShort = book != null ? book.getBookShort() : bookName;
            Integer testament = book != null ? book.getTestament() : null;
            
            String key = bookShort + ":" + chapter + ":" + verse;
            
            return new ScoredVerse(key, bookName, bookShort, chapter, verse, title, verseText, testament, score, score);
        } catch (Exception e) {
            log.debug("Failed to parse segment text: {}", text);
            return null;
        }
    }

    /**
     * Stage 2: Re-rank candidates and apply filters.
     */
    private List<VerseResult> rerankAndFilter(
            List<ScoredVerse> candidates,
            String query,
            double minScore,
            String versionFilter,
            ContextResult context,
            int maxResults) {

        String[] queryWords = query.toLowerCase().split("\\s+");

        return candidates.stream()
            .filter(sv -> matchesVersion(sv.bookShort, versionFilter))
            .filter(sv -> context.matchesVerse(sv.bookShort, sv.testament))
            .map(sv -> {
                double rerankedScore = calculateRerankedScore(sv, queryWords);
                return new ScoredVerse(sv.key, sv.bookName, sv.bookShort, sv.chapter, sv.verse,
                                      sv.title, sv.text, sv.testament, sv.score, rerankedScore);
            })
            .filter(sv -> sv.rerankedScore >= minScore)
            .sorted((a, b) -> Double.compare(b.rerankedScore, a.rerankedScore))
            .limit(maxResults)
            .map(sv -> VerseResult.builder()
                .reference(sv.bookName + " " + sv.chapter + ":" + sv.verse)
                .bookName(sv.bookName)
                .bookShort(sv.bookShort)
                .chapter(sv.chapter)
                .verse(sv.verse)
                .title(sv.title)
                .text(sv.text)
                .testament(sv.testament)
                .score(sv.score)
                .rerankedScore(sv.rerankedScore)
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Calculate re-ranked score combining multiple signals.
     */
    private double calculateRerankedScore(ScoredVerse sv, String[] queryWords) {
        double baseScore = sv.score;
        String verseText = sv.text != null ? sv.text.toLowerCase() : "";

        // Keyword boost
        double keywordBoost = 0.0;
        for (String word : queryWords) {
            if (word.length() > 2 && verseText.contains(word)) {
                keywordBoost += 0.05;
            }
        }
        keywordBoost = Math.min(keywordBoost, 0.2);

        // Length penalty
        double lengthFactor = 1.0;
        int textLength = sv.text != null ? sv.text.length() : 0;
        if (textLength > 300) {
            lengthFactor = 0.95;
        } else if (textLength > 500) {
            lengthFactor = 0.9;
        }

        double finalScore = (baseScore + keywordBoost) * lengthFactor;
        return Math.min(1.0, Math.max(0.0, finalScore));
    }

    /**
     * Check if a verse matches the version filter.
     */
    private boolean matchesVersion(BibleService.VerseResult verse, String versionFilter) {
        if (versionFilter == null || versionFilter.isBlank()) {
            return true;
        }
        // BibleService doesn't track version, so allow all for now
        return true;
    }

    private boolean matchesVersion(String bookShort, String versionFilter) {
        if (versionFilter == null || versionFilter.isBlank()) {
            return true;
        }
        return true;
    }

    /**
     * Check if a verse matches the context constraint.
     */
    private boolean matchesContext(BibleService.VerseResult verse, ContextResult context) {
        if (!context.hasContext()) {
            return true;
        }
        
        BibleService.Book book = bibleService.findBook(verse.getBookName());
        if (book == null) {
            return false;
        }
        
        return context.matchesVerse(book.getBookShort(), book.getTestament());
    }

    /**
     * Convert BibleService.VerseResult to model.VerseResult.
     */
    private VerseResult convertToVerseResult(BibleService.VerseResult v, double score, double rerankedScore) {
        BibleService.Book book = bibleService.findBook(v.getBookName());
        return VerseResult.builder()
            .reference(v.getReference())
            .bookName(v.getBookName())
            .bookShort(v.getBookShort())
            .chapter(v.getChapter())
            .verse(v.getVerse())
            .title(v.getTitle())
            .text(v.getText())
            .testament(book != null ? book.getTestament() : null)
            .score(score)
            .rerankedScore(rerankedScore)
            .build();
    }

    /**
     * Internal class for holding verse with scores.
     */
    private record ScoredVerse(
        String key,
        String bookName,
        String bookShort,
        Integer chapter,
        Integer verse,
        String title,
        String text,
        Integer testament,
        double score,
        double rerankedScore
    ) {}

    /**
     * Get service statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("candidateCount", candidateCount);
        stats.put("resultCount", resultCount);
        stats.put("minScore", minScore);
        stats.put("intentClassifier", intentClassifier.getStats());
        stats.put("contextClassifier", contextClassifier.getStats());
        return stats;
    }
}
