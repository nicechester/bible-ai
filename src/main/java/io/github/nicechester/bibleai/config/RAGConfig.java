package io.github.nicechester.bibleai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.github.nicechester.bibleai.store.SqliteEmbeddingStore;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Configuration for embedding model and vector store.
 * 
 * Embedding Store Priority:
 * 1. SQLite (if enabled) - fastest cold start, pre-built database in Docker image
 * 2. GCS (if enabled) - network load, still fast
 * 3. In-memory with generation - slowest, for development only
 */
@Log4j2
@Configuration
public class RAGConfig {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    // SQLite configuration (preferred for production)
    @Value("${bible.embedding.sqlite.enabled:false}")
    private boolean sqliteEnabled;

    @Value("${bible.embedding.sqlite.path:classpath:embeddings/bible-embeddings.db}")
    private String sqlitePath;

    // GCS configuration (fallback)
    @Value("${bible.embedding.gcs.enabled:false}")
    private boolean gcsEnabled;

    @Value("${bible.embedding.gcs.bucket:}")
    private String gcsBucket;

    @Value("${bible.embedding.gcs.blob-name:embeddings/bible-embeddings.json}")
    private String gcsBlobName;

    // Track how store was loaded (used by stats)
    @Getter
    private String loadedFrom = "generated";

    public RAGConfig(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> bibleEmbeddingStore(
            EmbeddingModel embeddingModel,
            @Value("${langchain4j.splitter.text.maxSegmentSize:500}") int maxSegmentSize,
            @Value("${langchain4j.splitter.text.maxOverlapSize:50}") int maxOverlapSize,
            @Value("${bible.data.json-path}") String bibleJsonPath,
            @Value("${bible.data.asv-json-path:classpath:bible/bible_asv.json}") String asvJsonPath) {
        
        // Priority 1: Try SQLite (fastest - local file, no network)
        if (sqliteEnabled) {
            EmbeddingStore<TextSegment> sqliteStore = tryLoadFromSqlite();
            if (sqliteStore != null) {
                loadedFrom = "sqlite";
                return sqliteStore;
            }
        }
        
        // Priority 2: Try GCS (network, but pre-computed)
        if (gcsEnabled && gcsBucket != null && !gcsBucket.isBlank()) {
            EmbeddingStore<TextSegment> gcsStore = tryLoadFromGcs();
            if (gcsStore != null) {
                loadedFrom = "gcs";
                return gcsStore;
            }
        }
        
        // Priority 3: Generate embeddings from Bible data
        loadedFrom = "generated";
        return generateEmbeddingStore(embeddingModel, maxSegmentSize, maxOverlapSize, bibleJsonPath, asvJsonPath);
    }
    
    /**
     * Try to load embedding store from SQLite database.
     */
    private EmbeddingStore<TextSegment> tryLoadFromSqlite() {
        try {
            log.info("Checking for SQLite embedding database: {}", sqlitePath);
            
            String resolvedPath = sqlitePath;
            
            // Handle classpath resources
            if (sqlitePath.startsWith("classpath:")) {
                Resource resource = resourceLoader.getResource(sqlitePath);
                
                if (!resource.exists()) {
                    log.info("SQLite database not found in classpath");
                    return null;
                }
                
                // Extract to temp file (SQLite needs file access)
                try (InputStream is = resource.getInputStream()) {
                    Path tempFile = Files.createTempFile("bible-embeddings", ".db");
                    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    resolvedPath = tempFile.toString();
                    log.info("Extracted SQLite database from classpath to: {}", resolvedPath);
                }
            } else {
                // Check if file exists
                if (!Files.exists(Path.of(resolvedPath))) {
                    log.info("SQLite database file not found: {}", resolvedPath);
                    return null;
                }
            }
            
            long startTime = System.currentTimeMillis();
            
            SqliteEmbeddingStore store = new SqliteEmbeddingStore(resolvedPath);
            
            if (!store.hasEmbeddings()) {
                log.info("SQLite database is empty");
                store.close();
                return null;
            }
            
            // Load embeddings into memory cache for fast searching
            store.loadCache();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Loaded {} embeddings from SQLite in {}ms", store.size(), duration);
            
            return store;
            
        } catch (Exception e) {
            log.warn("Failed to load embeddings from SQLite: {} - trying next option", e.getMessage());
            return null;
        }
    }

    /**
     * Try to load embedding store from GCS.
     */
    private EmbeddingStore<TextSegment> tryLoadFromGcs() {
        try {
            log.info("Checking GCS for pre-computed embeddings: gs://{}/{}", gcsBucket, gcsBlobName);
            
            Storage storage = StorageOptions.getDefaultInstance().getService();
            Blob blob = storage.get(BlobId.of(gcsBucket, gcsBlobName));
            
            if (blob == null || !blob.exists()) {
                log.info("No embeddings found in GCS");
                return null;
            }
            
            log.info("Loading embeddings from GCS...");
            long startTime = System.currentTimeMillis();
            
            byte[] content = blob.getContent();
            String json = new String(content, StandardCharsets.UTF_8);
            
            InMemoryEmbeddingStore<TextSegment> store = InMemoryEmbeddingStore.fromJson(json);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Loaded embeddings from GCS in {}ms ({} MB)", 
                    duration, content.length / 1024 / 1024);
            
            return store;
        } catch (Exception e) {
            log.warn("Failed to load embeddings from GCS: {} - will generate instead", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate embeddings from Bible JSON data (fallback).
     */
    private EmbeddingStore<TextSegment> generateEmbeddingStore(
            EmbeddingModel embeddingModel,
            int maxSegmentSize,
            int maxOverlapSize,
            String bibleJsonPath,
            String asvJsonPath) {
        
        try {
            StringBuilder bibleContent = new StringBuilder();
            
            // Load Korean Bible (개역개정)
            loadBibleJson(bibleJsonPath, bibleContent, "KRV");
            
            // Load English Bible (ASV) - better for embedding model
            try {
                loadBibleJson(asvJsonPath, bibleContent, "ASV");
            } catch (Exception e) {
                log.warn("Failed to load ASV Bible, continuing with Korean only: {}", e.getMessage());
            }
            
            if (bibleContent.length() == 0) {
                log.warn("No Bible content loaded. RAG will not have Bible context.");
                return new InMemoryEmbeddingStore<>();
            }
            
            log.info("Loaded Bible content ({} characters)", bibleContent.length());
            
            // Create document from Bible content
            Document bibleDoc = Document.from(bibleContent.toString());
            
            // Split into segments using DocumentSplitters
            List<TextSegment> segments = DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize)
                    .split(bibleDoc);
            
            log.info("Split Bible content into {} segments", segments.size());
            
            // Create embeddings
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            
            // Store in in-memory embedding store
            EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
            store.addAll(embeddings, segments);
            
            log.info("Loaded {} Bible segments into embedding store", segments.size());
            
            return store;
        } catch (Exception e) {
            log.error("Failed to load Bible data for RAG", e);
            return new InMemoryEmbeddingStore<>();
        }
    }
    
    private void loadBibleJson(String jsonPath, StringBuilder content, String version) throws Exception {
        Resource resource = resourceLoader.getResource(jsonPath);
        if (!resource.exists()) {
            log.warn("Bible JSON file not found: {}", jsonPath);
            return;
        }
        
        InputStream inputStream = resource.getInputStream();
        JsonNode root = objectMapper.readTree(inputStream);
        
        String versionName = root.has("version") ? root.get("version").asText() : version;
        log.info("Loading {} Bible from: {}", versionName, jsonPath);
        
        JsonNode booksNode = root.get("books");
        if (booksNode != null && booksNode.isArray()) {
            int bookCount = 0;
            int verseCount = 0;
            
            for (JsonNode bookNode : booksNode) {
                String bookName = bookNode.get("bookName").asText();
                String bookShort = bookNode.get("bookShort").asText();
                
                JsonNode chaptersNode = bookNode.get("chapters");
                if (chaptersNode != null && chaptersNode.isArray()) {
                    for (JsonNode chapterNode : chaptersNode) {
                        int chapterNum = chapterNode.get("chapter").asInt();
                        
                        JsonNode versesNode = chapterNode.get("verses");
                        if (versesNode != null && versesNode.isArray()) {
                            for (JsonNode verseNode : versesNode) {
                                int verseNum = verseNode.get("verse").asInt();
                                String text = verseNode.has("text") ? verseNode.get("text").asText() : "";
                                String title = verseNode.has("title") && !verseNode.get("title").isNull() 
                                    ? verseNode.get("title").asText() : null;
                                
                                // Format: "[ASV] Genesis 1:1 <Title> Text" or "[KRV] 창세기 1:1 <제목> 본문"
                                content.append("[").append(versionName).append("] ")
                                    .append(bookName)
                                    .append(" ").append(chapterNum).append(":").append(verseNum);
                                if (title != null && !title.isEmpty()) {
                                    content.append(" <").append(title).append(">");
                                }
                                if (text != null && !text.isEmpty()) {
                                    content.append(" ").append(text);
                                }
                                content.append("\n");
                                verseCount++;
                            }
                        }
                    }
                    bookCount++;
                }
            }
            
            log.info("Loaded {} books, {} verses from {} Bible", bookCount, verseCount, versionName);
        }
        
        inputStream.close();
    }

    @Bean
    public ContentRetriever bibleRetriever(
            EmbeddingStore<TextSegment> bibleEmbeddingStore,
            EmbeddingModel embeddingModel,
            @Value("${bible.rag.max-results:3}") int maxResults,
            @Value("${bible.rag.min-score:0.6}") double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(bibleEmbeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }
}

