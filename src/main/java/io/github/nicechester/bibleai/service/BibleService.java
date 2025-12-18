package io.github.nicechester.bibleai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for loading and querying Bible data from JSON file.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class BibleService {
    
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    
    @Value("${bible.data.json-path}")
    private String bibleJsonPath;
    
    private List<Book> books = new ArrayList<>();
    private Map<String, Book> bookMapByShort = new HashMap<>();
    private Map<String, Book> bookMapByName = new HashMap<>();
    
    @Data
    public static class Book {
        private String bookShort;
        private String bookName;
        private Integer testament;
        private Integer bookNumber;
        private List<Chapter> chapters = new ArrayList<>();
    }
    
    @Data
    public static class Chapter {
        private Integer chapter;
        private List<Verse> verses = new ArrayList<>();
    }
    
    @Data
    public static class Verse {
        private Integer verse;
        private String title;
        private String text;
    }
    
    @Data
    public static class VerseResult {
        private String bookName;
        private String bookShort;
        private Integer chapter;
        private Integer verse;
        private String title;
        private String text;
        private String reference; // e.g., "창세기 1:1"
    }
    
    @PostConstruct
    public void loadBibleData() {
        try {
            Resource resource = resourceLoader.getResource(bibleJsonPath);
            InputStream inputStream = resource.getInputStream();
            JsonNode root = objectMapper.readTree(inputStream);
            
            JsonNode booksNode = root.get("books");
            if (booksNode != null && booksNode.isArray()) {
                for (JsonNode bookNode : booksNode) {
                    Book book = objectMapper.treeToValue(bookNode, Book.class);
                    if (book != null) {
                        // Ensure lists are initialized
                        if (book.getChapters() == null) {
                            book.setChapters(new ArrayList<>());
                        }
                        // Initialize verses for each chapter
                        if (book.getChapters() != null) {
                            for (Chapter chapter : book.getChapters()) {
                                if (chapter != null && chapter.getVerses() == null) {
                                    chapter.setVerses(new ArrayList<>());
                                }
                            }
                        }
                        books.add(book);
                        if (book.getBookShort() != null) {
                            bookMapByShort.put(book.getBookShort(), book);
                        }
                        if (book.getBookName() != null) {
                            bookMapByName.put(book.getBookName(), book);
                        }
                    }
                }
            }
            
            log.info("Loaded {} books from Bible data", books.size());
        } catch (IOException e) {
            log.error("Failed to load Bible data from {}", bibleJsonPath, e);
            throw new RuntimeException("Failed to load Bible data", e);
        }
    }
    
    /**
     * Get a specific verse by book name, chapter, and verse number.
     */
    public VerseResult getVerse(String bookName, int chapter, int verse) {
        Book book = findBook(bookName);
        if (book == null) {
            return null;
        }
        
        List<Chapter> chapters = book.getChapters();
        if (chapters == null) {
            return null;
        }
        
        Chapter ch = chapters.stream()
            .filter(c -> c != null && c.getChapter() != null && c.getChapter() == chapter)
            .findFirst()
            .orElse(null);
        
        if (ch == null) {
            return null;
        }
        
        List<Verse> verses = ch.getVerses();
        if (verses == null) {
            return null;
        }
        
        Verse v = verses.stream()
            .filter(verseObj -> verseObj.getVerse() == verse)
            .findFirst()
            .orElse(null);
        
        if (v == null) {
            return null;
        }
        
        return createVerseResult(book, chapter, v);
    }
    
    /**
     * Get all verses in a chapter.
     */
    public List<VerseResult> getChapter(String bookName, int chapter) {
        Book book = findBook(bookName);
        if (book == null) {
            return Collections.emptyList();
        }
        
        List<Chapter> chapters = book.getChapters();
        if (chapters == null) {
            return Collections.emptyList();
        }
        
        Chapter ch = chapters.stream()
            .filter(c -> c != null && c.getChapter() != null && c.getChapter() == chapter)
            .findFirst()
            .orElse(null);
        
        if (ch == null) {
            return Collections.emptyList();
        }
        
        List<Verse> verses = ch.getVerses();
        if (verses == null) {
            return Collections.emptyList();
        }
        
        return verses.stream()
            .filter(v -> v != null)
            .map(v -> createVerseResult(book, chapter, v))
            .collect(Collectors.toList());
    }
    
    /**
     * Search for verses containing the given keyword.
     */
    public List<VerseResult> searchVerses(String keyword) {
        List<VerseResult> results = new ArrayList<>();
        
        for (Book book : books) {
            List<Chapter> chapters = book.getChapters();
            if (chapters == null) {
                continue;
            }
            for (Chapter chapter : chapters) {
                List<Verse> verses = chapter.getVerses();
                if (verses == null) {
                    continue;
                }
                for (Verse verse : verses) {
                    if (verse != null && verse.getText() != null && verse.getText().contains(keyword)) {
                        results.add(createVerseResult(book, chapter.getChapter(), verse));
                    }
                }
            }
        }
        
        return results;
    }
    
    /**
     * Search for verses by phrase (exact or partial match).
     */
    public List<VerseResult> searchByPhrase(String phrase) {
        List<VerseResult> results = new ArrayList<>();
        String lowerPhrase = phrase.toLowerCase();
        
        for (Book book : books) {
            List<Chapter> chapters = book.getChapters();
            if (chapters == null) {
                continue;
            }
            for (Chapter chapter : chapters) {
                List<Verse> verses = chapter.getVerses();
                if (verses == null) {
                    continue;
                }
                for (Verse verse : verses) {
                    if (verse != null && verse.getText() != null) {
                        String lowerText = verse.getText().toLowerCase();
                        if (lowerText.contains(lowerPhrase)) {
                            results.add(createVerseResult(book, chapter.getChapter(), verse));
                        }
                    }
                }
            }
        }
        
        return results;
    }
    
    /**
     * Get verses in a range (e.g., 창세기 1:1-10).
     */
    public List<VerseResult> getVerseRange(String bookName, int chapter, int startVerse, int endVerse) {
        Book book = findBook(bookName);
        if (book == null) {
            return Collections.emptyList();
        }
        
        List<Chapter> chapters = book.getChapters();
        if (chapters == null) {
            return Collections.emptyList();
        }
        
        Chapter ch = chapters.stream()
            .filter(c -> c != null && c.getChapter() != null && c.getChapter() == chapter)
            .findFirst()
            .orElse(null);
        
        if (ch == null) {
            return Collections.emptyList();
        }
        
        List<Verse> verses = ch.getVerses();
        if (verses == null) {
            return Collections.emptyList();
        }
        
        return verses.stream()
            .filter(v -> v != null && v.getVerse() != null && v.getVerse() >= startVerse && v.getVerse() <= endVerse)
            .map(v -> createVerseResult(book, chapter, v))
            .collect(Collectors.toList());
    }
    
    /**
     * Get verses with context (surrounding verses).
     */
    public List<VerseResult> getVerseWithContext(String bookName, int chapter, int verse, int contextVerses) {
        Book book = findBook(bookName);
        if (book == null) {
            return Collections.emptyList();
        }
        
        List<Chapter> chapters = book.getChapters();
        if (chapters == null) {
            return Collections.emptyList();
        }
        
        Chapter ch = chapters.stream()
            .filter(c -> c != null && c.getChapter() != null && c.getChapter() == chapter)
            .findFirst()
            .orElse(null);
        
        if (ch == null) {
            return Collections.emptyList();
        }
        
        List<Verse> verses = ch.getVerses();
        if (verses == null) {
            return Collections.emptyList();
        }
        
        int startVerse = Math.max(1, verse - contextVerses);
        int endVerse = Math.min(verses.size(), verse + contextVerses);
        
        return verses.stream()
            .filter(v -> v != null && v.getVerse() != null && v.getVerse() >= startVerse && v.getVerse() <= endVerse)
            .map(v -> createVerseResult(book, chapter, v))
            .collect(Collectors.toList());
    }
    
    /**
     * Get statistics about a keyword (how many times it appears, in which books, etc.).
     * @param keyword The keyword to search for
     * @param filterTestament Optional filter: "구약" (1) or "신약" (2), or null for all
     * @param filterBookType Optional filter: "선지서", "복음서", etc., or null for all
     */
    public Map<String, Object> getKeywordStatistics(String keyword, Integer filterTestament, String filterBookType) {
        Map<String, Integer> bookCounts = new HashMap<>();
        int totalCount = 0;
        List<String> sampleReferences = new ArrayList<>();
        
        for (Book book : books) {
            // Filter by testament if specified
            if (filterTestament != null && book.getTestament() != null && !book.getTestament().equals(filterTestament)) {
                continue;
            }
            
            // Filter by book type if specified (e.g., "선지서")
            if (filterBookType != null && !matchesBookType(book, filterBookType)) {
                continue;
            }
            
            int bookCount = 0;
            List<Chapter> chapters = book.getChapters();
            if (chapters == null) {
                continue;
            }
            
            for (Chapter chapter : chapters) {
                List<Verse> verses = chapter.getVerses();
                if (verses == null) {
                    continue;
                }
                
                for (Verse verse : verses) {
                    if (verse != null && verse.getText() != null && verse.getText().contains(keyword)) {
                        bookCount++;
                        totalCount++;
                        if (sampleReferences.size() < 5) {
                            sampleReferences.add(String.format("%s %d:%d", 
                                book.getBookName(), chapter.getChapter(), verse.getVerse()));
                        }
                    }
                }
            }
            if (bookCount > 0) {
                bookCounts.put(book.getBookName(), bookCount);
            }
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("keyword", keyword);
        stats.put("totalOccurrences", totalCount);
        stats.put("bookCounts", bookCounts);
        stats.put("sampleReferences", sampleReferences);
        stats.put("booksWithKeyword", bookCounts.size());
        
        return stats;
    }
    
    /**
     * Overloaded method without filters.
     */
    public Map<String, Object> getKeywordStatistics(String keyword) {
        return getKeywordStatistics(keyword, null, null);
    }
    
    /**
     * Check if a book matches a book type filter.
     */
    private boolean matchesBookType(Book book, String bookType) {
        String bookName = book.getBookName();
        if (bookName == null) {
            return false;
        }
        
        // 선지서 (Prophets)
        if (bookType.contains("선지서") || bookType.contains("선지")) {
            String[] prophets = {"이사야", "예레미야", "예레미야애가", "에스겔", "다니엘", 
                               "호세아", "요엘", "아모스", "오바댜", "요나", 
                               "미가", "나훔", "하박국", "스바냐", "학개", "스가랴", "말라기"};
            for (String prophet : prophets) {
                if (bookName.contains(prophet)) {
                    return true;
                }
            }
            return false;
        }
        
        // 복음서 (Gospels)
        if (bookType.contains("복음서") || bookType.contains("복음")) {
            return bookName.contains("복음");
        }
        
        // 서신서 (Epistles)
        if (bookType.contains("서신서") || bookType.contains("서신")) {
            String[] epistles = {"로마서", "고린도전서", "고린도후서", "갈라디아서", "에베소서",
                               "빌립보서", "골로새서", "데살로니가전서", "데살로니가후서",
                               "디모데전서", "디모데후서", "디도서", "빌레몬서", "히브리서",
                               "야고보서", "베드로전서", "베드로후서", "요한일서", "요한이서", "요한삼서", "유다서"};
            for (String epistle : epistles) {
                if (bookName.contains(epistle)) {
                    return true;
                }
            }
            return false;
        }
        
        // 기본적으로 bookName에 bookType이 포함되어 있으면 매치
        return bookName.contains(bookType);
    }
    
    /**
     * Get all books.
     */
    public List<Book> getAllBooks() {
        return new ArrayList<>(books);
    }
    
    /**
     * Get book by name or short name.
     */
    public Book findBook(String bookName) {
        if (bookName == null || bookName.isEmpty()) {
            return null;
        }
        
        // Try exact match first
        Book book = bookMapByName.get(bookName);
        if (book != null) {
            return book;
        }
        
        book = bookMapByShort.get(bookName);
        if (book != null) {
            return book;
        }
        
        // Try partial match
        for (Book b : books) {
            if (b == null) {
                continue;
            }
            String bName = b.getBookName();
            String bShort = b.getBookShort();
            
            if (bName != null && (bName.contains(bookName) || bookName.contains(bName))) {
                return b;
            }
            if (bShort != null && bShort.equals(bookName)) {
                return b;
            }
        }
        
        return null;
    }
    
    private VerseResult createVerseResult(Book book, int chapter, Verse verse) {
        if (book == null || verse == null) {
            return null;
        }
        
        VerseResult result = new VerseResult();
        result.setBookName(book.getBookName() != null ? book.getBookName() : "");
        result.setBookShort(book.getBookShort() != null ? book.getBookShort() : "");
        result.setChapter(chapter);
        result.setVerse(verse.getVerse() != null ? verse.getVerse() : 0);
        result.setTitle(verse.getTitle());
        result.setText(verse.getText());
        
        String bookName = book.getBookName() != null ? book.getBookName() : "Unknown";
        int verseNum = verse.getVerse() != null ? verse.getVerse() : 0;
        result.setReference(String.format("%s %d:%d", bookName, chapter, verseNum));
        return result;
    }
}

