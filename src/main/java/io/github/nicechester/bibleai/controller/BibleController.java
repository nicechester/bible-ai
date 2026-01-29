package io.github.nicechester.bibleai.controller;

import io.github.nicechester.bibleai.agent.BibleAgent;
import io.github.nicechester.bibleai.model.QueryRequest;
import io.github.nicechester.bibleai.model.QueryResponse;
import io.github.nicechester.bibleai.model.SearchResponse;
import io.github.nicechester.bibleai.service.BibleService;
import io.github.nicechester.bibleai.service.SmartBibleSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping("/api/bible")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BibleController {
    
    private final BibleAgent bibleAgent;
    private final BibleService bibleService;
    private final SmartBibleSearchService smartSearchService;
    
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            String userQuery = request.query();
            String sessionId = request.sessionId();
            log.info("Received query: {} (sessionId: {})", userQuery, 
                sessionId != null ? sessionId : "none");
            
            // Get response from agent with session ID
            String agentResponse = bibleAgent.handleQuery(userQuery, sessionId);
            
            return ResponseEntity.ok(QueryResponse.success(agentResponse, null, null));
        } catch (Exception e) {
            log.error("Query processing failed", e);
            return ResponseEntity.ok(QueryResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Get a full chapter for the Bible reader.
     */
    @GetMapping("/{book}/{chapter}")
    public ResponseEntity<Map<String, Object>> getChapter(
            @PathVariable String book,
            @PathVariable int chapter,
            @RequestParam(required = false) String version) {
        
        log.info("Getting chapter: {} {} (version: {})", book, chapter, version);
        
        try {
            List<BibleService.VerseResult> verses = bibleService.getChapter(book, chapter);
            
            if (verses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            BibleService.Book bookInfo = bibleService.findBook(book);
            int totalChapters = bookInfo != null ? bookInfo.getChapters().size() : 0;
            
            Map<String, Object> response = new HashMap<>();
            response.put("bookName", bookInfo != null ? bookInfo.getBookName() : book);
            response.put("bookShort", bookInfo != null ? bookInfo.getBookShort() : book);
            response.put("chapter", chapter);
            response.put("totalChapters", totalChapters);
            response.put("verses", verses.stream()
                .map(v -> Map.of(
                    "verse", v.getVerse(),
                    "text", v.getText(),
                    "title", v.getTitle() != null ? v.getTitle() : ""
                ))
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get chapter: {} {}", book, chapter, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get a specific verse.
     */
    @GetMapping("/{book}/{chapter}/{verse}")
    public ResponseEntity<Map<String, Object>> getVerse(
            @PathVariable String book,
            @PathVariable int chapter,
            @PathVariable int verse) {
        
        log.info("Getting verse: {} {}:{}", book, chapter, verse);
        
        try {
            BibleService.VerseResult result = bibleService.getVerse(book, chapter, verse);
            
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            
            BibleService.Book bookInfo = bibleService.findBook(book);
            
            Map<String, Object> response = new HashMap<>();
            response.put("reference", result.getReference());
            response.put("bookName", result.getBookName());
            response.put("bookShort", result.getBookShort());
            response.put("chapter", result.getChapter());
            response.put("verse", result.getVerse());
            response.put("text", result.getText());
            response.put("title", result.getTitle() != null ? result.getTitle() : "");
            response.put("testament", bookInfo != null ? bookInfo.getTestament() : null);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get verse: {} {}:{}", book, chapter, verse, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get verse with surrounding context.
     */
    @GetMapping("/{book}/{chapter}/{verse}/context")
    public ResponseEntity<Map<String, Object>> getVerseWithContext(
            @PathVariable String book,
            @PathVariable int chapter,
            @PathVariable int verse,
            @RequestParam(defaultValue = "2") int size) {
        
        log.info("Getting verse with context: {} {}:{} (size: {})", book, chapter, verse, size);
        
        try {
            List<BibleService.VerseResult> verses = bibleService.getVerseWithContext(book, chapter, verse, size);
            
            if (verses.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            BibleService.Book bookInfo = bibleService.findBook(book);
            
            Map<String, Object> response = new HashMap<>();
            response.put("bookName", bookInfo != null ? bookInfo.getBookName() : book);
            response.put("bookShort", bookInfo != null ? bookInfo.getBookShort() : book);
            response.put("chapter", chapter);
            response.put("targetVerse", verse);
            response.put("contextSize", size);
            response.put("verses", verses.stream()
                .map(v -> Map.of(
                    "verse", v.getVerse(),
                    "text", v.getText(),
                    "title", v.getTitle() != null ? v.getTitle() : "",
                    "isTarget", v.getVerse() == verse
                ))
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get verse with context: {} {}:{}", book, chapter, verse, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Smart search API endpoint.
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        Integer maxResults = (Integer) request.get("maxResults");
        Double minScore = request.get("minScore") != null ? 
            ((Number) request.get("minScore")).doubleValue() : null;
        String version = (String) request.get("version");
        
        log.info("Smart search: {} (maxResults: {}, minScore: {}, version: {})", 
                query, maxResults, minScore, version);
        
        SearchResponse response = smartSearchService.search(query, maxResults, minScore, version);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(Map.of(
            "version", "개역개정",
            "language", "ko",
            "totalBooks", "66"
        ));
    }
    
    /**
     * Get agent and search statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("agent", bibleAgent.getStats());
        stats.put("search", smartSearchService.getStats());
        return ResponseEntity.ok(stats);
    }
}

