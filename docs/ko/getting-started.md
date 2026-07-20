# 시작하기

이 가이드는 橘子岛 설치, 첫 API 키 추가, 첫 메시지 전송 과정을 안내합니다.

## 설치

### F-Droid에서 (권장)

橘子岛는 오픈 소스 Android 앱 스토어인 F-Droid에서 이용 가능합니다.

1. 기기에 [F-Droid](https://f-droid.org/) 설치
2. F-Droid를 열고 **橘子岛** 검색
3. **설치** 탭

### GitHub 릴리스에서

1. [릴리스 페이지](https://github.com/orangeisland/app/releases) 방문
2. 최신 `.apk` 파일 다운로드
3. 기기에서 파일을 열고 설치 확인

### 소스에서 빌드

직접 빌드하는 경우:

1. 저장소 클론:
   ```
   git clone https://github.com/orangeisland/app.git
   ```
2. [Android Studio](https://developer.android.com/studio) (Ladybug 이상)에서 프로젝트 열기
3. Gradle 동기화 및 빌드

요구 사항: Android SDK 34+, JDK 17+.

---

## 첫 실행

橘子岛를 처음 열면 텍스트 입력이 있는 환영 화면이 표시됩니다. 채팅하기 전에 프로바이더와 API 키를 구성해야 합니다.

### 1단계: API 키 추가

1. 내비게이션 바에서 **설정** 아이콘(우측 하단 톱니바퀴) 탭
2. **서비스** 아래에서 **프로바이더** 탭
3. 목록에서 프로바이더 선택 (예: **OpenAI**, **Anthropic**, **Google**)
4. **새 키 추가** 탭
5. 키 이름 입력 (예: "개인용") 및 API 키 붙여넣기
6. **추가** 탭

??? tip "API 키는 어디서 받나요?"
    - **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — 무료 티어 이용 가능
    - **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic**: [Console API Keys](https://console.anthropic.com/)
    - **DeepSeek**: [Platform](https://platform.deepseek.com/)
    - **OpenRouter**: [Keys page](https://openrouter.ai/keys)

    각 프로바이더에 대한 자세한 내용은 [API 프로바이더](provider.ko.md) 페이지를 참조하세요.

### 2단계: 모델 동기화

1. 설정으로 돌아가서 **모델** 탭 (**서비스** 아래)
2. **모든 프로바이더에서 동기화** 탭
3. 橘子岛가 구성된 모든 프로바이더의 최신 모델 목록을 가져옵니다
4. 동기화 후, 모델을 탭하여 **기본 모델**로 설정

### 3단계: 첫 메시지 보내기

1. **뒤로 가기 화살표**를 탭하여 채팅 화면으로 돌아가기
2. 하단 입력 필드에 메시지 입력
3. **보내기** (종이 비행기 아이콘) 탭

모델이 실시간으로 응답을 스트리밍합니다.

---

## 앱 레이아웃

橘子岛는 채팅 화면을 중심으로 한 깔끔한 레이아웃을 제공합니다:

### 상단 바

- **대화 제목** — 현재 대화 이름 표시 (탭하여 이름 변경)
- **햄버거 메뉴** (:material-menu:) — 대화 서랍 열기
- **오버플로 메뉴** (:material-dots-vertical:) — 대화별 설정 (모델, 시스템 프롬프트, 생성 매개변수)

### 대화 서랍

**햄버거 메뉴**를 탭하거나 왼쪽 가장자리에서 오른쪽으로 스와이프하여 열기:

- **검색 바** — 키워드 또는 시맨틱 검색으로 이전 대화 찾기
- **대화 목록** — 모든 대화, 최신순
- **설정** (:material-cog:) — 프로바이더, 모델, 프롬프트 등 구성
- **새 채팅** — 새 대화 시작

### 채팅 화면

- **메시지 영역** — 마크다운 렌더링이 포함된 스크롤 가능한 대화 기록
- **하단 바** — 텍스트 입력, 모델 선택기, 첨부 파일 버튼 (+), 보내기 버튼

---

## 다음 단계

- [시스템 프롬프트 구성](system-prompts.ko.md)으로 모델 행동 사용자 지정
- [웹 검색 설정](web-search.ko.md)으로 실시간 인터넷 접근
- [에이전트 도구 탐색](tools.ko.md) — 셸 실행, 파일 작업, 메모리
- [데이터 가져오기](import-export.ko.md) Claude 또는 ChatGPT에서
- [로컬 모델 실행](local-model.ko.md)으로 오프라인 사용
