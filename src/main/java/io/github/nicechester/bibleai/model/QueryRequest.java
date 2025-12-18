package io.github.nicechester.bibleai.model;

public record QueryRequest(
    String query,
    String sessionId
) {}

