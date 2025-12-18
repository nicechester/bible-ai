package io.github.nicechester.bibleai.model;

import java.util.List;
import java.util.Map;

public record QueryResponse(
    String summary,
    List<Map<String, Object>> results,
    String sql,
    boolean success
) {
    public static QueryResponse success(String summary, List<Map<String, Object>> results, String sql) {
        return new QueryResponse(summary, results, sql, true);
    }
    
    public static QueryResponse error(String error) {
        return new QueryResponse(error, null, null, false);
    }
}

