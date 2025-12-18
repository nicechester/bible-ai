package io.github.nicechester.bibleai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Log4j2
@Configuration
public class RAGConfig {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

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

