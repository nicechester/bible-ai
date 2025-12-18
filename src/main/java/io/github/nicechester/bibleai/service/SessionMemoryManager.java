package io.github.nicechester.bibleai.service;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages session-based chat memories for different users/sessions.
 * Automatically cleans up old sessions to prevent memory leaks.
 */
@Log4j2
@Service
public class SessionMemoryManager {
    
    private static final int MAX_MESSAGES_PER_SESSION = 20;
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    
    private final Map<String, SessionMemoryEntry> sessionMemories = new ConcurrentHashMap<>();
    
    public ChatMemory getOrCreateMemory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("No session ID provided, creating ephemeral memory");
            return MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES_PER_SESSION);
        }
        
        SessionMemoryEntry entry = sessionMemories.compute(sessionId, (key, existing) -> {
            if (existing == null) {
                log.info("Creating new session memory for sessionId: {}", sessionId);
                ChatMemory newMemory = MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES_PER_SESSION);
                return new SessionMemoryEntry(newMemory, Instant.now());
            } else {
                existing.updateLastAccess();
                return existing;
            }
        });
        
        return entry.memory();
    }
    
    public void clearSession(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            SessionMemoryEntry removed = sessionMemories.remove(sessionId);
            if (removed != null) {
                log.info("Cleared session memory for sessionId: {}", sessionId);
            }
        }
    }
    
    public int getActiveSessionCount() {
        return sessionMemories.size();
    }
    
    @Scheduled(fixedRate = 10 * 60 * 1000) // Every 10 minutes
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        int cleanedCount = 0;
        
        for (Map.Entry<String, SessionMemoryEntry> entry : sessionMemories.entrySet()) {
            long idleTimeMs = now.toEpochMilli() - entry.getValue().lastAccess().toEpochMilli();
            if (idleTimeMs > SESSION_TIMEOUT_MS) {
                sessionMemories.remove(entry.getKey());
                cleanedCount++;
                log.debug("Cleaned up expired session: {} (idle for {}ms)", entry.getKey(), idleTimeMs);
            }
        }
        
        if (cleanedCount > 0) {
            log.info("Cleaned up {} expired sessions. Active sessions: {}", 
                cleanedCount, sessionMemories.size());
        }
    }
    
    private static class SessionMemoryEntry {
        private final ChatMemory memory;
        private volatile Instant lastAccess;
        
        public SessionMemoryEntry(ChatMemory memory, Instant lastAccess) {
            this.memory = memory;
            this.lastAccess = lastAccess;
        }
        
        public ChatMemory memory() {
            return memory;
        }
        
        public Instant lastAccess() {
            return lastAccess;
        }
        
        public void updateLastAccess() {
            this.lastAccess = Instant.now();
        }
    }
}

