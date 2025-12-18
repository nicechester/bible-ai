# Bible AI

A conversational Bible study agent for the Korean Revised Version (개역개정) Bible. Built with Spring Boot, LangChain4j, and Google Gemini AI.

## Features

### Core Capabilities
- **Natural Language Bible Search**: Search and explore the Bible through natural language
- **Verse Lookup**: Get specific verses by book, chapter, and verse number
- **Verse Range**: Get multiple verses in a range (e.g., 창세기 1:1-10)
- **Context-Aware Reading**: Get verses with surrounding context for better understanding
- **Keyword Search**: Find verses containing specific keywords or phrases
- **Chapter Reading**: Read entire chapters at once
- **Keyword Statistics**: Analyze how often words appear across the Bible with filtering options
- **RAG-Powered Context**: Uses embedded Bible text for semantic understanding
- **Session Management**: Maintains conversation context across multiple questions
- **Mermaid Diagrams**: Visual representation of genealogy, relationships, and concepts
- **Cursor-like UI**: Split-pane interface with conversation on left, preview on right

### Advanced Features
- **Filtered Statistics**: Search by testament (구약/신약) and book type (선지서/복음서/서신서)
- **Smart Context Retrieval**: Automatically finds relevant verses using semantic search
- **Multi-turn Conversations**: Maintains context across multiple questions within a session
- **Error Recovery**: Automatically clears corrupted sessions to prevent error propagation

## Architecture

### Tech Stack
- **Backend**: Spring Boot 3.5.4 with Java 25
- **AI Framework**: LangChain4j 1.2.0
- **LLM**: Google Gemini 2.5 Flash (via `langchain4j-google-ai-gemini`)
- **Bible Data**: 개역개정 (Korean Revised Version) - 66 books, 31,173 verses
- **RAG**: In-memory embedding store with All-MiniLM-L6-v2 quantized embeddings (ONNX-based)
- **Frontend**: React 18 (in-browser, served as static files)

### Key Components
- **BibleAgent**: LangChain4j AI service with session-based chat memory
- **SessionMemoryManager**: Manages isolated conversation history per session (auto-cleanup after 30 min)
- **BibleTools**: Tool-calling interface with 8 tools:
  - `getVerse`: Get specific verse
  - `getChapter`: Get all verses in a chapter
  - `getVerseRange`: Get verses in a range
  - `getVerseWithContext`: Get verse with surrounding context
  - `searchVerses`: Search by keyword
  - `searchByPhrase`: Search by phrase
  - `getKeywordStatistics`: Get statistics with optional filters
  - `getAllBooks`: List all Bible books
- **BibleService**: Loads and queries Bible data from JSON file
- **RAGConfig**: Loads and embeds Bible text for semantic retrieval using ONNX quantized model

## Setup

1. **Configure Environment Variables**:
   ```bash
   export GEMINI_API_KEY=your-google-api-key
   export GEMINI_MODEL_NAME=gemini-2.5-flash-lite  # Optional, defaults to gemini-2.5-flash-lite
   export BIBLE_JSON_PATH=classpath:bible/bible_krv.json  # Optional
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
- "창세기 1장 1절을 보여줘"
- "요한복음 3장 16절"

**Verse Range:**
- "창세기 1장 1-10절"
- "마태복음 5장 1-12절"

**Context-Aware Reading:**
- "요한복음 3:16 주변 구절도 보여줘"
- "사랑에 대한 구절 주변 맥락과 함께"

**Chapter Reading:**
- "마태복음 5장을 읽어줘"
- "시편 23장 전체"

**Keyword Search:**
- "사랑에 대한 구절을 찾아줘"
- "믿음에 관한 말씀"
- "하나님의 은혜"

**Statistics with Filters:**
- "사랑이라는 단어가 성경에 몇 번 나와?"
- "사랑이라는 단어가 구약 선지서에 몇 번 나와?"
- "믿음이 신약 복음서에 몇 번 나와?"

**Topic Exploration:**
- "예수님의 계보를 그림으로 설명해줘"
- "예수님의 비유를 설명해줘"
- "십계명이 뭐야?"
- "구약과 신약의 차이는?"

**Genealogy & Diagrams:**
- "예수님의 계보를 그림으로 설명해줘"
- "아브라함부터 다윗까지의 계보"

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
│   └── bible_krv.json         # Full Bible text (66 books, 31,173 verses)
└── static/          # Frontend:
    └── index.html             # React 18 SPA with session management
```

## Bible Data

The Bible data is stored in `src/main/resources/bible/bible_krv.json` with the following structure:

```json
{
  "version": "개역개정",
  "language": "ko",
  "totalBooks": 66,
  "books": [
    {
      "bookShort": "창",
      "bookName": "창세기",
      "testament": 1,
      "bookNumber": 1,
      "chapters": [
        {
          "chapter": 1,
          "verses": [
            {
              "verse": 1,
              "title": "천지 창조",
              "text": "태초에 하나님이 천지를 창조하시니라"
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
   "query": "창세기 1장 1절을 보여줘",
   "sessionId": "session-1702857890-abc123"  // Optional
 }
```

**Response:**
```json
{
   "summary": "창세기 1:1 <천지 창조> 태초에 하나님이 천지를 창조하시니라...",
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
   "version": "개역개정",
   "language": "ko",
   "totalBooks": "66"
 }
```

## Development

### Data Processing

The Bible text files were parsed from UTF-8 text files using `parse_bible.py`:

```bash
cd trashcan/bible
python3 parse_bible.py
```

This generates `bible_krv.json` from the text files in `trashcan/bible/data/개역개정-text-utf8/`.

The parsing script:
- Converts EUC-KR encoded files to UTF-8
- Parses verse format: `책약자:장:절 <제목> 본문`
- Generates structured JSON with 66 books and 31,173 verses

### RAG Configuration

- **Embedding Model**: All-MiniLM-L6-v2 (quantized, ONNX-based)
- **Chunk size**: 500 characters
- **Overlap**: 50 characters
- **Max results**: 3 retrieved segments per query
- **Min score**: 0.6 (similarity threshold)

**Note**: The ONNX runtime logs during startup are normal - they indicate the quantized embedding model is being loaded. This model runs locally without requiring external API calls.

### Available Tools

The AI agent has access to 8 tools:

1. **getVerse(bookName, chapter, verse)**: Get a specific verse
2. **getChapter(bookName, chapter)**: Get all verses in a chapter
3. **getVerseRange(bookName, chapter, startVerse, endVerse)**: Get verses in a range
4. **getVerseWithContext(bookName, chapter, verse, contextVerses)**: Get verse with surrounding context
5. **searchVerses(keyword)**: Search for verses containing a keyword
6. **searchByPhrase(phrase)**: Search for verses containing a phrase
7. **getKeywordStatistics(keyword, testament, bookType)**: Get statistics with optional filters
   - `testament`: 1 for 구약, 2 for 신약, null for all
   - `bookType`: "선지서", "복음서", "서신서", null for all
8. **getAllBooks()**: List all Bible books

### Mermaid Diagram Support

The AI can generate Mermaid diagrams for:
- Genealogy (예수님의 계보)
- Relationships between concepts
- Visual representations of Bible structures

**Important**: Diagrams use `flowchart TD` syntax with Korean text properly quoted: `A["한글텍스트"]`

## Advantages Over Simple Chat

See [USAGE_EXAMPLES.md](./USAGE_EXAMPLES.md) for detailed comparison with simple Gemini chat.

Key advantages:
- ✅ **Accurate Data Access**: Direct access to actual Bible database (66 books, 31,173 verses)
- ✅ **Statistics**: Precise word frequency analysis with filtering
- ✅ **Context**: Automatic surrounding verse context
- ✅ **Structured Search**: Systematic search across entire Bible
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
```

## Troubleshooting

### Common Issues

**1. ONNX Runtime Logs**
- **Cause**: Normal behavior - embedding model initialization
- **Explanation**: All-MiniLM-L6-v2 quantized model uses ONNX Runtime
- **Action**: No action needed, these logs are expected

**2. Mermaid Diagram Syntax Errors**
- **Cause**: Incorrect Mermaid syntax or Korean character encoding
- **Fix**: System prompt has been updated to use `flowchart TD` with quoted Korean text
- **Format**: `A["한글텍스트"]` (double quotes required)

**3. "parts is null" Error**
- **Cause**: Complex queries trying to process multiple items simultaneously
- **Fix**: System prompt updated to handle complex requests more safely
- **Workaround**: Break complex requests into simpler parts

**4. Session Memory Errors**
- **Cause**: Corrupted session state
- **Fix**: Sessions are automatically cleared on errors
- **Action**: Start a new session with the + button

### Performance Notes

- RAG retrieval adds ~100-200ms per query
- Session cleanup is non-blocking (scheduled task every 10 minutes)
- ChatMemory limited to 20 messages per session
- Embedding model loads once at startup (ONNX initialization)
- Frontend is fully client-side (no server-side rendering)

## License

This project uses the Korean Revised Version (개역개정) Bible text.
