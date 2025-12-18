package io.github.nicechester.bibleai.controller;

import io.github.nicechester.bibleai.agent.BibleAgent;
import io.github.nicechester.bibleai.model.QueryRequest;
import io.github.nicechester.bibleai.model.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequestMapping("/api/bible")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BibleController {
    
    private final BibleAgent bibleAgent;
    
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
    
    @GetMapping("/config")
    public ResponseEntity<java.util.Map<String, String>> getConfig() {
        return ResponseEntity.ok(java.util.Map.of(
            "version", "개역개정",
            "language", "ko",
            "totalBooks", "66"
        ));
    }
}

