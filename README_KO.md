# Bible AI

개역개정 성경을 위한 대화형 성경 공부 에이전트. Spring Boot, LangChain4j, Google Gemini AI로 구축되었습니다.

## 주요 기능

### 핵심 기능
- **자연어 성경 검색**: 자연어로 성경을 검색하고 탐색
- **구절 조회**: 책, 장, 절 번호로 특정 구절 조회
- **구절 범위 조회**: 여러 구절을 범위로 조회 (예: 창세기 1:1-10)
- **맥락 인식 읽기**: 주변 구절을 포함하여 더 나은 이해를 위한 조회
- **키워드 검색**: 특정 키워드나 구문이 포함된 구절 찾기
- **장 읽기**: 전체 장을 한 번에 읽기
- **키워드 통계**: 필터링 옵션과 함께 성경 전체에서 단어 출현 빈도 분석
- **RAG 기반 맥락**: 의미 이해를 위한 임베딩된 성경 텍스트 사용
- **세션 관리**: 여러 질문에 걸쳐 대화 맥락 유지
- **Mermaid 다이어그램**: 계보, 관계, 개념의 시각적 표현
- **Cursor 스타일 UI**: 왼쪽 대화, 오른쪽 미리보기 분할 패널 인터페이스

### 고급 기능
- **필터링 통계**: 성경 구분(구약/신약) 및 책 유형(선지서/복음서/서신서)으로 검색
- **스마트 맥락 검색**: 의미 기반 검색으로 관련 구절 자동 찾기
- **다중 턴 대화**: 세션 내 여러 질문에 걸쳐 맥락 유지
- **에러 복구**: 에러 전파를 방지하기 위해 손상된 세션 자동 정리

## 아키텍처

### 기술 스택
- **백엔드**: Spring Boot 3.5.4 with Java 25
- **AI 프레임워크**: LangChain4j 1.2.0
- **LLM**: Google Gemini 2.5 Flash (`langchain4j-google-ai-gemini` 사용)
- **성경 데이터**: 개역개정 (Korean Revised Version) - 66권, 31,173절
- **RAG**: All-MiniLM-L6-v2 양자화 임베딩을 사용한 인메모리 임베딩 스토어 (ONNX 기반)
- **프론트엔드**: React 18 (브라우저 내부, 정적 파일로 제공)

### 주요 컴포넌트
- **BibleAgent**: 세션 기반 채팅 메모리를 가진 LangChain4j AI 서비스
- **SessionMemoryManager**: 세션별 격리된 대화 기록 관리 (30분 후 자동 정리)
- **BibleTools**: 8개의 도구를 가진 도구 호출 인터페이스:
  - `getVerse`: 특정 구절 조회
  - `getChapter`: 장의 모든 구절 조회
  - `getVerseRange`: 범위 내 구절 조회
  - `getVerseWithContext`: 주변 맥락과 함께 구절 조회
  - `searchVerses`: 키워드로 검색
  - `searchByPhrase`: 구문으로 검색
  - `getKeywordStatistics`: 선택적 필터와 함께 통계 조회
  - `getAllBooks`: 모든 성경 책 목록
- **BibleService**: JSON 파일에서 성경 데이터 로드 및 쿼리
- **RAGConfig**: ONNX 양자화 모델을 사용하여 의미 검색을 위한 성경 텍스트 로드 및 임베딩

## 설치 및 실행

1. **환경 변수 설정**:
   ```bash
   export GEMINI_API_KEY=your-google-api-key
   export GEMINI_MODEL_NAME=gemini-2.5-flash-lite  # 선택사항, 기본값: gemini-2.5-flash-lite
   export BIBLE_JSON_PATH=classpath:bible/bible_krv.json  # 선택사항
   ```

2. **빌드 및 실행**:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

3. **UI 접속**:
   브라우저에서 http://localhost:8080 열기

## 사용 방법

### 질문 예시

**구절 조회:**
- "창세기 1장 1절을 보여줘"
- "요한복음 3장 16절"

**구절 범위:**
- "창세기 1장 1-10절"
- "마태복음 5장 1-12절"

**맥락 인식 읽기:**
- "요한복음 3:16 주변 구절도 보여줘"
- "사랑에 대한 구절 주변 맥락과 함께"

**장 읽기:**
- "마태복음 5장을 읽어줘"
- "시편 23장 전체"

**키워드 검색:**
- "사랑에 대한 구절을 찾아줘"
- "믿음에 관한 말씀"
- "하나님의 은혜"

**필터링 통계:**
- "사랑이라는 단어가 성경에 몇 번 나와?"
- "사랑이라는 단어가 구약 선지서에 몇 번 나와?"
- "믿음이 신약 복음서에 몇 번 나와?"

**주제 탐색:**
- "예수님의 계보를 그림으로 설명해줘"
- "예수님의 비유를 설명해줘"
- "십계명이 뭐야?"
- "구약과 신약의 차이는?"

**계보 및 다이어그램:**
- "예수님의 계보를 그림으로 설명해줘"
- "아브라함부터 다윗까지의 계보"

## 프로젝트 구조

```
src/main/java/io/github/nicechester/bibleai/
├── agent/           # BibleAgent (세션 관리가 있는 LangChain4j AI 서비스)
├── config/          # 설정 빈:
│   ├── LLMConfig           # Gemini ChatModel 설정
│   └── RAGConfig           # 임베딩 모델 및 RAG 설정
├── controller/      # REST 엔드포인트:
│   └── BibleController     # POST /api/bible/query, GET /api/bible/config
├── model/           # Request/Response DTO:
│   ├── QueryRequest        # 선택적 sessionId가 있는 쿼리
│   └── QueryResponse      # 요약이 포함된 응답
├── service/         # 핵심 서비스:
│   ├── SessionMemoryManager    # 세션 기반 채팅 메모리 관리
│   └── BibleService            # 성경 데이터 로드 및 쿼리
└── tool/            # LangChain4j @Tool 주석 메서드:
    └── BibleTools              # 성경 작업을 위한 8개 도구

src/main/resources/
├── bible/           # 성경 데이터:
│   └── bible_krv.json         # 전체 성경 텍스트 (66권, 31,173절)
└── static/          # 프론트엔드:
    └── index.html             # 세션 관리가 있는 React 18 SPA
```

## 성경 데이터

성경 데이터는 `src/main/resources/bible/bible_krv.json`에 다음 구조로 저장됩니다:

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

## API 엔드포인트

### POST `/api/bible/query`
성경에 대한 자연어 쿼리 실행.

**요청:**
```json
{
   "query": "창세기 1장 1절을 보여줘",
   "sessionId": "session-1702857890-abc123"  // 선택사항
 }
```

**응답:**
```json
{
   "summary": "창세기 1:1 <천지 창조> 태초에 하나님이 천지를 창조하시니라...",
   "results": null,
   "sql": null,
   "success": true
 }
```

### GET `/api/bible/config`
성경 설정 및 버전 조회.

**응답:**
```json
{
   "version": "개역개정",
   "language": "ko",
   "totalBooks": "66"
 }
```

## 개발

### 데이터 처리

성경 텍스트 파일은 `parse_bible.py`를 사용하여 UTF-8 텍스트 파일에서 파싱되었습니다:

```bash
cd trashcan/bible
python3 parse_bible.py
```

이것은 `trashcan/bible/data/개역개정-text-utf8/`의 텍스트 파일에서 `bible_krv.json`을 생성합니다.

파싱 스크립트는:
- EUC-KR 인코딩 파일을 UTF-8로 변환
- 구절 형식 파싱: `책약자:장:절 <제목> 본문`
- 66권과 31,173절이 포함된 구조화된 JSON 생성

### RAG 설정

- **임베딩 모델**: All-MiniLM-L6-v2 (양자화, ONNX 기반)
- **청크 크기**: 500자
- **오버랩**: 50자
- **최대 결과**: 쿼리당 3개의 검색된 세그먼트
- **최소 점수**: 0.6 (유사도 임계값)

**참고**: 시작 시 ONNX 런타임 로그는 정상입니다 - 양자화된 임베딩 모델이 로드되고 있음을 나타냅니다. 이 모델은 외부 API 호출 없이 로컬에서 실행됩니다.

### 사용 가능한 도구

AI 에이전트는 8개의 도구에 접근할 수 있습니다:

1. **getVerse(bookName, chapter, verse)**: 특정 구절 조회
2. **getChapter(bookName, chapter)**: 장의 모든 구절 조회
3. **getVerseRange(bookName, chapter, startVerse, endVerse)**: 범위 내 구절 조회
4. **getVerseWithContext(bookName, chapter, verse, contextVerses)**: 주변 맥락과 함께 구절 조회
5. **searchVerses(keyword)**: 키워드가 포함된 구절 검색
6. **searchByPhrase(phrase)**: 구문이 포함된 구절 검색
7. **getKeywordStatistics(keyword, testament, bookType)**: 선택적 필터와 함께 통계 조회
   - `testament`: 1은 구약, 2는 신약, null은 전체
   - `bookType`: "선지서", "복음서", "서신서", null은 전체
8. **getAllBooks()**: 모든 성경 책 목록

### Mermaid 다이어그램 지원

AI는 다음을 위한 Mermaid 다이어그램을 생성할 수 있습니다:
- 계보 (예수님의 계보)
- 개념 간의 관계
- 성경 구조의 시각적 표현

**중요**: 다이어그램은 한글 텍스트를 따옴표로 감싼 `flowchart TD` 구문을 사용합니다: `A["한글텍스트"]`

## 단순 채팅보다 나은 점

간단한 Gemini 채팅과의 상세한 비교는 [USAGE_EXAMPLES.md](./USAGE_EXAMPLES.md)를 참조하세요.

주요 장점:
- ✅ **정확한 데이터 접근**: 실제 성경 데이터베이스 직접 접근 (66권, 31,173절)
- ✅ **통계**: 필터링과 함께 정확한 단어 빈도 분석
- ✅ **맥락**: 자동 주변 구절 맥락 제공
- ✅ **구조화된 검색**: 전체 성경에 걸친 체계적 검색
- ✅ **대화**: 세션 기반 다중 턴 대화 지원

## 설정

### 애플리케이션 설정 (`application.yml`)

**LLM (Gemini):**
```yaml
langchain4j:
  llm:
    gemini:
      model-name: ${GEMINI_MODEL_NAME:gemini-2.5-flash-lite}
      api-key: ${GEMINI_API_KEY:}
```

**RAG 설정:**
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

**성경 데이터:**
```yaml
bible:
  data:
    json-path: ${BIBLE_JSON_PATH:classpath:bible/bible_krv.json}
```

## 문제 해결

### 일반적인 문제

**1. ONNX 런타임 로그**
- **원인**: 정상 동작 - 임베딩 모델 초기화
- **설명**: All-MiniLM-L6-v2 양자화 모델이 ONNX 런타임을 사용
- **조치**: 조치 불필요, 이러한 로그는 예상된 동작입니다

**2. Mermaid 다이어그램 구문 오류**
- **원인**: 잘못된 Mermaid 구문 또는 한글 문자 인코딩
- **수정**: 시스템 프롬프트가 따옴표로 감싼 한글 텍스트와 함께 `flowchart TD`를 사용하도록 업데이트됨
- **형식**: `A["한글텍스트"]` (쌍따옴표 필요)

**3. "parts is null" 오류**
- **원인**: 여러 항목을 동시에 처리하려는 복잡한 쿼리
- **수정**: 시스템 프롬프트가 복잡한 요청을 더 안전하게 처리하도록 업데이트됨
- **해결 방법**: 복잡한 요청을 더 간단한 부분으로 나누기

**4. 세션 메모리 오류**
- **원인**: 손상된 세션 상태
- **수정**: 오류 시 세션이 자동으로 정리됨
- **조치**: + 버튼으로 새 세션 시작

### 성능 참고사항

- RAG 검색은 쿼리당 ~100-200ms 추가
- 세션 정리는 논블로킹 (10분마다 스케줄된 작업)
- ChatMemory는 세션당 20개 메시지로 제한
- 임베딩 모델은 시작 시 한 번 로드됨 (ONNX 초기화)
- 프론트엔드는 완전히 클라이언트 사이드 (서버 사이드 렌더링 없음)

## 라이선스

이 프로젝트는 개역개정 성경 텍스트를 사용합니다.

