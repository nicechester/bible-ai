package io.github.nicechester.bibleai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Response intent classifier that determines how the user wants the response presented.
 * 
 * Uses embedding similarity to classify response types:
 * - DIAGRAM: User wants visual representation (mermaid, flowchart, chart)
 * - EXPLANATION: User wants detailed explanation with context
 * - LIST: User wants a simple list of verses
 * - STATISTICS: User wants counts and statistics
 * - CONTEXT: User wants surrounding verses for context
 * - DIRECT: Simple lookup, no LLM needed
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ResponseIntentClassifier {

    private final EmbeddingModel embeddingModel;

    public enum ResponseType {
        /** User wants visual representation (mermaid diagram, flowchart) */
        DIAGRAM,
        /** User wants detailed explanation with context */
        EXPLANATION,
        /** User wants a simple list of verses */
        LIST,
        /** User wants counts and statistics */
        STATISTICS,
        /** User wants surrounding verses for context */
        CONTEXT,
        /** Simple lookup, no LLM needed */
        DIRECT
    }

    // Pre-computed embeddings for prototypes
    private final List<Embedding> diagramPrototypeEmbeddings = new ArrayList<>();
    private final List<Embedding> explanationPrototypeEmbeddings = new ArrayList<>();
    private final List<Embedding> statisticsPrototypeEmbeddings = new ArrayList<>();
    private final List<Embedding> contextPrototypeEmbeddings = new ArrayList<>();
    private final List<Embedding> listPrototypeEmbeddings = new ArrayList<>();

    // Prototype phrases for DIAGRAM responses
    private static final List<String> DIAGRAM_PROTOTYPES = List.of(
        // Korean - various patterns for "with diagram/picture"
        "그림으로 설명해줘",
        "그림과 함께 설명해줘",
        "그림으로 보여줘",
        "그림과 함께 보여줘",
        "그림으로 정리해줘",
        "다이어그램으로 보여줘",
        "다이어그램으로 설명해줘",
        "도표로 정리해줘",
        "도표로 설명해줘",
        "시각적으로 정리해줘",
        "시각적으로 설명해줘",
        "차트로 그려줘",
        "차트로 보여줘",
        "flowchart로 그려줘",
        "관계도로 보여줘",
        "계보를 그림으로",
        "족보를 도표로",
        "흐름도로 설명해줘",
        "그래프로 보여줘",
        // English
        "explain with a diagram",
        "explain with a picture",
        "show me in a chart",
        "draw a flowchart",
        "visualize the relationship",
        "show the genealogy",
        "create a visual representation",
        "show me visually"
    );

    // Prototype phrases for EXPLANATION responses
    private static final List<String> EXPLANATION_PROTOTYPES = List.of(
        // Korean
        "설명해줘",
        "알려줘",
        "가르쳐줘",
        "의미를 알려줘",
        "뜻이 뭐야",
        "해석해줘",
        "풀이해줘",
        "상세하게 알려줘",
        "자세히 설명해줘",
        // English
        "explain this to me",
        "what does this mean",
        "tell me about",
        "teach me about",
        "interpret this verse",
        "give me a detailed explanation"
    );

    // Prototype phrases for STATISTICS responses
    private static final List<String> STATISTICS_PROTOTYPES = List.of(
        // Korean
        "몇 번 나와",
        "몇 개야",
        "얼마나 자주",
        "통계를 알려줘",
        "횟수가 어떻게 돼",
        "몇 권에 나와",
        "어느 책에 많이 나와",
        // English
        "how many times",
        "how often",
        "show me statistics",
        "count the occurrences",
        "which books mention",
        "frequency of"
    );

    // Prototype phrases for CONTEXT responses
    private static final List<String> CONTEXT_PROTOTYPES = List.of(
        // Korean
        "주변 구절도 보여줘",
        "문맥을 알려줘",
        "앞뒤 구절",
        "전후 관계",
        "맥락을 설명해줘",
        "앞뒤로 몇 절",
        // English
        "show surrounding verses",
        "give me the context",
        "verses before and after",
        "what comes before",
        "read the passage"
    );

    // Prototype phrases for LIST responses (simple search)
    private static final List<String> LIST_PROTOTYPES = List.of(
        // Korean
        "찾아줘",
        "보여줘",
        "나열해줘",
        "구절을 알려줘",
        "어디 나와",
        "어디에 있어",
        // English
        "find verses",
        "show me verses",
        "list the passages",
        "where does it say",
        "which verses mention"
    );

    // Thresholds
    private static final double DIAGRAM_THRESHOLD = 0.50;
    private static final double STATS_THRESHOLD = 0.50;
    private static final double CONTEXT_THRESHOLD = 0.48;
    private static final double EXPLANATION_THRESHOLD = 0.45;

    @PostConstruct
    public void initializePrototypes() {
        log.info("Initializing response intent classifier");
        
        long startTime = System.currentTimeMillis();

        // Pre-compute embeddings for each response type
        for (String prototype : DIAGRAM_PROTOTYPES) {
            diagramPrototypeEmbeddings.add(embeddingModel.embed(prototype).content());
        }
        for (String prototype : EXPLANATION_PROTOTYPES) {
            explanationPrototypeEmbeddings.add(embeddingModel.embed(prototype).content());
        }
        for (String prototype : STATISTICS_PROTOTYPES) {
            statisticsPrototypeEmbeddings.add(embeddingModel.embed(prototype).content());
        }
        for (String prototype : CONTEXT_PROTOTYPES) {
            contextPrototypeEmbeddings.add(embeddingModel.embed(prototype).content());
        }
        for (String prototype : LIST_PROTOTYPES) {
            listPrototypeEmbeddings.add(embeddingModel.embed(prototype).content());
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Response intent classifier initialized in {}ms ({} prototypes)", 
                duration, 
                DIAGRAM_PROTOTYPES.size() + EXPLANATION_PROTOTYPES.size() + 
                STATISTICS_PROTOTYPES.size() + CONTEXT_PROTOTYPES.size() + LIST_PROTOTYPES.size());
    }

    // Keywords that strongly indicate DIAGRAM intent
    private static final List<String> DIAGRAM_KEYWORDS = List.of(
        "그림", "다이어그램", "도표", "차트", "flowchart", "관계도", "계보도", 
        "족보도", "흐름도", "시각", "그래프", "diagram", "chart", "visual"
    );

    /**
     * Classify the response type of a query.
     */
    public ResponseType classify(String query) {
        if (query == null || query.isBlank()) {
            return ResponseType.LIST;
        }

        String trimmed = query.trim();
        String lowerQuery = trimmed.toLowerCase();

        // Keyword-based boost for DIAGRAM (explicit visual request)
        boolean hasDiagramKeyword = DIAGRAM_KEYWORDS.stream()
                .anyMatch(keyword -> lowerQuery.contains(keyword.toLowerCase()));

        // Embed the query
        Embedding queryEmbedding = embeddingModel.embed(trimmed).content();

        // Calculate similarities using MAX (best match) instead of average
        double diagramScore = calculateMaxSimilarity(queryEmbedding, diagramPrototypeEmbeddings);
        double explanationScore = calculateMaxSimilarity(queryEmbedding, explanationPrototypeEmbeddings);
        double statisticsScore = calculateMaxSimilarity(queryEmbedding, statisticsPrototypeEmbeddings);
        double contextScore = calculateMaxSimilarity(queryEmbedding, contextPrototypeEmbeddings);
        double listScore = calculateMaxSimilarity(queryEmbedding, listPrototypeEmbeddings);

        // Apply keyword boost for diagram
        if (hasDiagramKeyword) {
            diagramScore = Math.min(1.0, diagramScore + 0.25);
            log.debug("Diagram keyword boost applied for '{}'", trimmed);
        }

        log.debug("Response scores for '{}': diagram={:.3f}, explain={:.3f}, stats={:.3f}, context={:.3f}, list={:.3f}", 
                 trimmed, diagramScore, explanationScore, statisticsScore, contextScore, listScore);

        // Priority-based classification
        // DIAGRAM has highest priority if it matches (with keyword or high embedding score)
        if ((hasDiagramKeyword && diagramScore > 0.40) || 
            (diagramScore > DIAGRAM_THRESHOLD && diagramScore > explanationScore && diagramScore > listScore)) {
            log.debug("Response type: DIAGRAM (score: {:.0f}%, keyword: {})", diagramScore * 100, hasDiagramKeyword);
            return ResponseType.DIAGRAM;
        }

        // STATISTICS has high priority (allows direct response without LLM)
        if (statisticsScore > STATS_THRESHOLD && statisticsScore > listScore) {
            log.debug("Response type: STATISTICS (score: {:.0f}%)", statisticsScore * 100);
            return ResponseType.STATISTICS;
        }

        // CONTEXT if asking for surrounding verses
        if (contextScore > CONTEXT_THRESHOLD && contextScore > listScore && contextScore > explanationScore) {
            log.debug("Response type: CONTEXT (score: {:.0f}%)", contextScore * 100);
            return ResponseType.CONTEXT;
        }

        // EXPLANATION for detailed explanations
        if (explanationScore > EXPLANATION_THRESHOLD && explanationScore > listScore) {
            log.debug("Response type: EXPLANATION (score: {:.0f}%)", explanationScore * 100);
            return ResponseType.EXPLANATION;
        }

        // Default to LIST (simple verse lookup)
        log.debug("Response type: LIST (default)");
        return ResponseType.LIST;
    }

    /**
     * Check if LLM is needed for this response type.
     */
    public boolean requiresLLM(ResponseType type) {
        return switch (type) {
            case DIAGRAM, EXPLANATION, CONTEXT -> true;
            case STATISTICS, LIST, DIRECT -> false;
        };
    }

    /**
     * Calculate MAX cosine similarity between query and prototype embeddings.
     * Uses best match instead of average for better detection.
     */
    private double calculateMaxSimilarity(Embedding query, List<Embedding> prototypes) {
        if (prototypes.isEmpty()) return 0.0;

        double maxSimilarity = 0.0;
        for (Embedding prototype : prototypes) {
            double similarity = cosineSimilarity(query.vector(), prototype.vector());
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
            }
        }
        return maxSimilarity;
    }

    /**
     * Calculate cosine similarity between two vectors.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    /**
     * Get classifier statistics for debugging.
     */
    public String getStats() {
        return String.format("ResponseIntentClassifier: %d diagram, %d explanation, %d statistics, %d context, %d list prototypes",
                diagramPrototypeEmbeddings.size(), explanationPrototypeEmbeddings.size(),
                statisticsPrototypeEmbeddings.size(), contextPrototypeEmbeddings.size(),
                listPrototypeEmbeddings.size());
    }
}
