# 橘子岛 사용자 매뉴얼

橘子岛 사용자 매뉴얼에 오신 것을 환영합니다. 橘子岛는 멀티 프로바이더 접근, 비선형 분기 대화, 에이전트 도구 호출, 원격 기기 제어를 지원하는 Android용 BYOK(Bring Your Own Key) LLM 클라이언트입니다.

## 빠른 링크

### 시작하기

- **[시작하기](getting-started.ko.md)** — 설치, 설정 및 첫 메시지 보내기
- **[FAQ](faq.ko.md)** — 자주 묻는 질문에 대한 답변

### 핵심 기능

- **[대화](conversations.ko.md)** — 비선형 분기, 메시지 작업, 스트리밍, 마크다운 렌더링
- **[API 프로바이더](provider.ko.md)** — OpenAI, Anthropic, Google, DeepSeek, Ollama 및 사용자 지정 엔드포인트 연결
- **[모델](models.ko.md)** — 모델 활성화/비활성화, 별칭, 프로바이더별 모델 동기화
- **[시스템 프롬프트](system-prompts.ko.md)** — 3섹션 편집기, 변수 치환, 대화별 전환
- **[생성](generation.ko.md)** — temperature, top P, max tokens, thinking, frequency/presence penalties
- **[제목 생성](title-generation.ko.md)** — 대화 제목 자동 생성
- **[이미지 트랜스크립션](transcription.ko.md)** — 비전 미지원 프로바이더를 위한 이미지-텍스트 파이프라인
- **[이미지 생성](image-generation.ko.md)** — 채팅 도구로서의 텍스트-이미지 생성
- **[외관](appearance.ko.md)** — 테마 모드, 색상 구성, 동적 색상, 구성 스타일

### 에이전트 도구

- **[개요](tools.ko.md)** — 다중 라운드 도구 호출 작동 방식
- **[웹 검색](web-search.ko.md)** — DuckDuckGo Lite, Brave, Serper, Tavily, SearXNG 통합
- **[원격 셸 (Conch)](shell.ko.md)** — 암호화된 원격 명령 실행, 파일 작업, MCP 통합
- **[샌드박스](sandbox.ko.md)** — 격리된 명령 실행을 위한 로컬 Alpine Linux 환경

### 지식 관리

- **[대화 검색](search.ko.md)** — 채팅 기록에 대한 키워드 및 시맨틱(RAG) 검색
- **[임베딩 / RAG](embedding.ko.md)** — 시맨틱 검색을 위한 임베딩 모델 설정
- **[메모리 & 캐시](memory.ko.md)** — 활성 메모리, 저장된 메모리, 자동 캐싱

### 더 보기

- **[로컬 모델](local-model.ko.md)** — llama.cpp를 통해 기기에서 GGUF 모델 실행
- **[PDF 가져오기](pdf-import.ko.md)** — PDF 페이지 추출 및 비전 모델로 전송
- **[데이터 이식성](import-export.ko.md)** — .橘子岛 파일 내보내기/가져오기, 자동 백업, Claude 및 ChatGPT에서 가져오기
- **[언어](language.ko.md)** — English, 中文, 繁體中文 또는 시스템 기본값 간 전환
- **[정보](about.ko.md)** — 버전 정보, 업데이트, 문서 토글, 링크, 평가

---

## 橘子岛 소개

橘子岛는 AI 파워 유저를 위한 BYOK Android 클라이언트입니다:

- **중간자 없음**: 직접 API 연결, 원격 측정 없음, 추적 없음
- **기기 내 저장**: 모든 것이 Room 데이터베이스에 로컬로 저장됩니다
- **비선형 대화**: 과거 메시지를 편집하고 대체 분기를 탐색하세요
- **기본 에이전트**: 웹 검색, 이미지 생성, 코드 실행, 셸, 파일 작업, 메모리를 포함한 다중 라운드 도구 호출
- **원격 제어**: 암호화된 Conch 프로토콜을 통해 서버 관리
- **오픈 소스**: MIT 라이선스, [GitHub 소스](https://github.com/chloemeadow0-code/Orange-Island)
