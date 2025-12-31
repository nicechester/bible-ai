# Bible AI

A conversational Bible study agent supporting both Korean Revised Version (KRV) and American Standard Version (ASV) Bibles. Built with Spring Boot, LangChain4j, and Google Gemini AI.

## Features

### Core Capabilities
- **Natural Language Bible Search**: Search and explore the Bible through natural language
- **Verse Lookup**: Get specific verses by book, chapter, and verse number
- **Verse Range**: Get multiple verses in a range (e.g., Genesis 1:1-10)
- **Context-Aware Reading**: Get verses with surrounding context for better understanding
- **Keyword Search**: Find verses containing specific keywords or phrases
- **Chapter Reading**: Read entire chapters at once
- **Keyword Statistics**: Analyze how often words appear across the Bible with filtering options
- **RAG-Powered Context**: Uses embedded Bible text for semantic understanding
- **Session Management**: Maintains conversation context across multiple questions
- **Mermaid Diagrams**: Visual representation of genealogy, relationships, and concepts
- **Cursor-like UI**: Split-pane interface with conversation on left, preview on right

### Advanced Features
- **Multi-language Support**: Korean (KRV) and English (ASV) Bible versions
- **Filtered Statistics**: Search by testament (Old Testament/New Testament) and book type (Prophets/Gospels/Epistles)
- **Semantic Search Tool**: Embedding-based search available as a tool (Reverse RAG pattern)
- **Multi-turn Conversations**: Maintains context across multiple questions within a session
- **Error Recovery**: Automatically clears corrupted sessions to prevent error propagation
- **Smart Memory Management**: Auto-cleanup to prevent Gemini API function calling issues

## Architecture

### Tech Stack
- **Backend**: Spring Boot 3.5.4 with Java 25
- **AI Framework**: LangChain4j 1.2.0
- **LLM**: Google Gemini 2.5 Flash (via `langchain4j-google-ai-gemini`)
- **Bible Data**: 
  - KRV (Korean Revised Version) - 66 books, 31,173 verses
  - ASV (American Standard Version) - 66 books, 85,920 verses
- **RAG**: In-memory embedding store with All-MiniLM-L6-v2 quantized embeddings (ONNX-based)
  - Uses Reverse RAG pattern: embedding search available as a tool, not automatic
- **Frontend**: React 18 (in-browser, served as static files)

### Key Components
- **BibleAgent**: LangChain4j AI service with session-based chat memory
- **SessionMemoryManager**: Manages isolated conversation history per session (max 10 messages, auto-cleanup after 30 min)
- **BibleTools**: Tool-calling interface with 9 tools:
  - `getVerse`: Get specific verse
  - `getChapter`: Get all verses in a chapter
  - `getVerseRange`: Get verses in a range
  - `getVerseWithContext`: Get verse with surrounding context
  - `searchVerses`: Search by keyword (preferred for Korean text)
  - `searchByPhrase`: Search by phrase (preferred for Korean text)
  - `getKeywordStatistics`: Get statistics with optional filters
  - `searchVersesBySemanticSimilarity`: Semantic search using embeddings (use with caution for Korean)
  - `getAllBooks`: List all Bible books
- **BibleService**: Loads and queries Bible data from JSON file
- **RAGConfig**: Loads and embeds Bible text for semantic retrieval using ONNX quantized model

## Setup

1. **Configure Environment Variables**:
   ```bash
   export GEMINI_API_KEY=your-google-api-key
   export GEMINI_MODEL_NAME=gemini-2.5-flash-lite  # Optional, defaults to gemini-2.5-flash-lite
   export BIBLE_JSON_PATH=classpath:bible/bible_krv.json  # Optional
   export BIBLE_ASV_JSON_PATH=classpath:bible/bible_asv.json  # Optional
   ```

2. **Build and Run**:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

3. **Access UI**:
   Open http://localhost:8080 in your browser

## Usage

### Query Examples

**Verse Lookup:**
- "Show me Genesis 1:1"
- "John 3:16"

**Verse Range:**
- "Genesis 1:1-10"
- "Matthew 5:1-12"

**Context-Aware Reading:**
- "Show me John 3:16 with surrounding verses"
- "Show me verses about love with context"

**Chapter Reading:**
- "Read Matthew chapter 5"
- "Psalm 23 entire chapter"

**Keyword Search:**
- "Find verses about love"
- "Verses about faith"
- "God's grace"

**Statistics with Filters:**
- "How many times does the word 'love' appear in the Bible?"
- "How many times does 'love' appear in Old Testament prophets?"
- "How many times does 'faith' appear in New Testament gospels?"

**Topic Exploration:**
- "Explain Jesus' genealogy with a diagram"
- "Explain Jesus' parables"
- "What are the Ten Commandments?"
- "What's the difference between Old and New Testament?"

**Genealogy & Diagrams:**
- "Explain Jesus' genealogy with a diagram"
- "Genealogy from Abraham to David"

## Project Structure

```
src/main/java/io/github/nicechester/bibleai/
├── agent/           # BibleAgent (LangChain4j AI service with session management)
├── config/          # Configuration beans:
│   ├── LLMConfig           # Gemini ChatModel configuration
│   └── RAGConfig           # Embedding model and RAG setup
├── controller/       # REST endpoints:
│   └── BibleController     # POST /api/bible/query, GET /api/bible/config
├── model/           # Request/Response DTOs:
│   ├── QueryRequest        # Query with optional sessionId
│   └── QueryResponse      # Response with summary
├── service/         # Core services:
│   ├── SessionMemoryManager    # Session-based chat memory management
│   └── BibleService            # Bible data loading and querying
└── tool/            # LangChain4j @Tool annotated methods:
    └── BibleTools              # 8 tools for Bible operations

src/main/resources/
├── bible/           # Bible data:
│   ├── bible_krv.json         # Korean Bible (KRV) - 66 books, 31,173 verses
│   └── bible_asv.json         # English Bible (ASV) - 66 books, 85,920 verses
└── static/          # Frontend:
    └── index.html             # React 18 SPA with session management
```

## Bible Data

The Bible data is stored in JSON files in `src/main/resources/bible/`:

- **bible_krv.json**: Korean Revised Version (KRV) - 66 books, 31,173 verses
- **bible_asv.json**: American Standard Version (ASV) - 66 books, 85,920 verses

Both versions are loaded into the embedding store for semantic search. The English version works better with the All-MiniLM-L6-v2 embedding model, improving semantic search accuracy.

The JSON structure:

```json
{
  "version": "KRV",
  "language": "ko",
  "totalBooks": 66,
  "books": [
    {
      "bookShort": "Gen",
      "bookName": "Genesis",
      "testament": 1,
      "bookNumber": 1,
      "chapters": [
        {
          "chapter": 1,
          "verses": [
            {
              "verse": 1,
              "title": "Creation",
              "text": "In the beginning God created the heavens and the earth"
            }
          ]
        }
      ]
    }
  ]
}
```

## API Endpoints

### POST `/api/bible/query`
Execute natural language queries about the Bible.

**Request:**
```json
{
   "query": "Show me Genesis 1:1",
   "sessionId": "session-1702857890-abc123"  // Optional
 }
```

**Response:**
```json
{
   "summary": "Genesis 1:1 <Creation> In the beginning God created the heavens and the earth...",
   "results": null,
   "sql": null,
   "success": true
 }
```

### GET `/api/bible/config`
Get Bible configuration and version.

**Response:**
```json
{
   "version": "KRV",
   "language": "ko",
   "totalBooks": "66"
 }
```

## Development

### Data Processing

The Bible text files were parsed using Python scripts:

**Korean Bible (KRV):**
```bash
cd trashcan/bible
python3 parse_bible.py
```
- Converts EUC-KR encoded files to UTF-8
- Parses verse format: `book_abbreviation:chapter:verse <title> text`
- Generates `bible_krv.json` with 66 books and 31,173 verses

**English Bible (ASV):**
```bash
cd trashcan/bible
python3 parse_asv.py
```
- Parses ASV text format: `Genesis`, `Chapter 1`, `1 In the beginning...`
- Generates `bible_asv.json` with 66 books and 85,920 verses

### RAG Configuration

- **Embedding Model**: All-MiniLM-L6-v2 (quantized, ONNX-based)
- **Bible Versions**: Both Korean (KRV) and English (ASV) loaded into embedding store
- **Chunk size**: 500 characters
- **Overlap**: 50 characters
- **Max results**: 3 retrieved segments per query
- **Min score**: 0.6 (similarity threshold)
- **Pattern**: Reverse RAG - embedding search available as a tool, not automatic

**Note**: 
- The ONNX runtime logs during startup are normal - they indicate the quantized embedding model is being loaded
- This model runs locally without requiring external API calls
- English text works better with the embedding model, improving semantic search accuracy
- Korean text semantic search has limitations; keyword-based tools are preferred

### Available Tools

The AI agent has access to 9 tools:

1. **getVerse(bookName, chapter, verse)**: Get a specific verse
2. **getChapter(bookName, chapter)**: Get all verses in a chapter
3. **getVerseRange(bookName, chapter, startVerse, endVerse)**: Get verses in a range
4. **getVerseWithContext(bookName, chapter, verse, contextVerses)**: Get verse with surrounding context
5. **searchVerses(keyword)**: Search for verses containing a keyword (PREFERRED for Korean text)
6. **searchByPhrase(phrase)**: Search for verses containing a phrase (PREFERRED for Korean text)
7. **getKeywordStatistics(keyword, testament, bookType)**: Get statistics with optional filters
   - `testament`: 1 for Old Testament, 2 for New Testament, null for all
   - `bookType`: "Prophets", "Gospels", "Epistles", null for all
8. **searchVersesBySemanticSimilarity(query, maxResults)**: Semantic search using embeddings
   - Use with caution for Korean text (embedding model limitations)
   - Works better with English queries
   - Always verify results are from correct books
9. **getAllBooks()**: List all Bible books

### Mermaid Diagram Support

The AI can generate Mermaid diagrams for:
- Genealogy (Jesus' genealogy)
- Relationships between concepts
- Visual representations of Bible structures

**Important**: Diagrams use `flowchart TD` syntax with text properly quoted: `A["Text"]`

## Advantages Over Simple Chat

See [USAGE_EXAMPLES.md](./USAGE_EXAMPLES.md) for detailed comparison with simple Gemini chat.

Key advantages:
- ✅ **Accurate Data Access**: Direct access to actual Bible database (Korean + English, ~117,000 verses)
- ✅ **Multi-language Support**: Both Korean and English Bible versions
- ✅ **Statistics**: Precise word frequency analysis with filtering
- ✅ **Context**: Automatic surrounding verse context
- ✅ **Structured Search**: Systematic search across entire Bible
- ✅ **Semantic Search**: Embedding-based search available as a tool (works better with English)
- ✅ **Conversation**: Session-based multi-turn dialogue support

## Configuration

### Application Settings (`application.yml`)

**LLM (Gemini):**
```yaml
langchain4j:
  llm:
    gemini:
      model-name: ${GEMINI_MODEL_NAME:gemini-2.5-flash-lite}
      api-key: ${GEMINI_API_KEY:}
```

**RAG Settings:**
```yaml
langchain4j:
  splitter:
    text:
      maxSegmentSize: 500
      maxOverlapSize: 50

bible:
  rag:
    max-results: 3
    min-score: 0.6
```

**Bible Data:**
```yaml
bible:
  data:
    json-path: ${BIBLE_JSON_PATH:classpath:bible/bible_krv.json}
    asv-json-path: ${BIBLE_ASV_JSON_PATH:classpath:bible/bible_asv.json}
  rag:
    max-results: 3
    min-score: 0.6
```

## Troubleshooting

### Common Issues

**1. ONNX Runtime Logs**
- **Cause**: Normal behavior - embedding model initialization
- **Explanation**: All-MiniLM-L6-v2 quantized model uses ONNX Runtime
- **Action**: No action needed, these logs are expected

**2. Mermaid Diagram Syntax Errors**
- **Cause**: Incorrect Mermaid syntax or character encoding
- **Fix**: System prompt has been updated to use `flowchart TD` with quoted text
- **Format**: `A["Text"]` (double quotes required)

**3. "parts is null" Error**
- **Cause**: Complex queries trying to process multiple items simultaneously
- **Fix**: System prompt updated to handle complex requests more safely
- **Workaround**: Break complex requests into simpler parts

**4. Session Memory Errors**
- **Cause**: Corrupted session state
- **Fix**: Sessions are automatically cleared on errors
- **Action**: Start a new session with the + button

**5. Gemini API Function Calling Errors**
- **Cause**: "function call turn comes immediately after a user turn" error
- **Explanation**: Gemini API requires function calls to follow specific message order
- **Fix**: Session memory automatically cleared when reaching 8 messages to prevent this
- **Action**: No action needed - handled automatically

### Performance Notes

- Semantic search adds ~100-200ms per query (when used as tool)
- Session cleanup is non-blocking (scheduled task every 10 minutes)
- ChatMemory limited to 10 messages per session (reduced to prevent Gemini API issues)
- Auto-cleanup at 8 messages to prevent function calling errors
- Embedding model loads once at startup (ONNX initialization)
- Both Korean and English Bibles loaded into embedding store (~117,000 verses total)
- Frontend is fully client-side (no server-side rendering)

## Additional Resources

- [Blue Letter Bible - Apps & Tools](https://www.blueletterbible.org/apps-tools.cfm) - Comprehensive Bible study tools and resources

## License

MIT License - see [LICENSE](LICENSE) file for details.

This project uses:
- Korean Revised Version (KRV) Bible text
- American Standard Version (ASV) Bible text (Public Domain)

---

**Author**: Chester Kim  
**Date**: December 29, 2025
