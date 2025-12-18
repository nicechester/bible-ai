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
            @Value("${bible.data.json-path}") String bibleJsonPath) {
        
        try {
            StringBuilder bibleContent = new StringBuilder();
            
            // Load Bible JSON file
            Resource resource = resourceLoader.getResource(bibleJsonPath);
            InputStream inputStream = resource.getInputStream();
            JsonNode root = objectMapper.readTree(inputStream);
            
            JsonNode booksNode = root.get("books");
            if (booksNode != null && booksNode.isArray()) {
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
                                    
                                    // Format: "창세기 1:1 <제목> 본문"
                                    bibleContent.append(bookName)
                                        .append(" ").append(chapterNum).append(":").append(verseNum);
                                    if (title != null && !title.isEmpty()) {
                                        bibleContent.append(" <").append(title).append(">");
                                    }
                                    if (text != null && !text.isEmpty()) {
                                        bibleContent.append(" ").append(text);
                                    }
                                    bibleContent.append("\n");
                                }
                            }
                        }
                    }
                }
            }
            
            inputStream.close();
            
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

