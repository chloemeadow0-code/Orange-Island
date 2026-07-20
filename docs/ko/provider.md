# API 프로바이더

橘子岛는 AI 프로바이더에 직접 연결합니다 — 중개자 없음, 구독 없음, 원격 측정 없음. 직접 API 키를 가져오고 모든 것이 기기에서 실행됩니다.

## 내장 프로바이더

| 프로바이더 | 기본 URL | 모델 | 참고 |
|----------|----------|--------|-------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Gemini 시리즈 | Google AI Studio를 통한 무료 티어 이용 가능 |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4, GPT-4o, o-시리즈 | 추론 모델 지원 |
| **Anthropic** | `https://api.anthropic.com/v1` | Claude 시리즈 | 확장 thinking 지원 |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3, DeepSeek-R1 | 추론 모델 지원 |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Qwen 시리즈 | Alibaba DashScope 경유 |
| **Ollama** | `http://localhost:11434/v1` | 풀링된 모든 모델 | 자체 호스팅, API 키 불필요 |
| **OpenRouter** | `https://openrouter.ai/api/v1` | 멀티 프로바이더 | 하나의 API로 여러 모델 접근 |
| **로컬** | N/A | GGUF 모델 | llama.cpp 경유 기기 내, 완전 오프라인 |

## 프로바이더 전환

설정에서 프로바이더 선택기를 탭하여 프로바이더 간 전환. 각 프로바이더는 자체적으로 다음을 유지합니다:

- API 키
- 기본 URL (프록시/자체 호스팅용 편집 가능)
- 모델 목록

---

## API 키

### 프로바이더당 여러 키

각 프로바이더는 여러 개의 이름 지정된 API 키를 지원합니다. 이를 통해:

- **순환** — 다른 사용량 티어에 따라 키 전환
- **조직** — 업무용과 개인용 분리
- **폴백** — 백업 키 준비

### 키 관리

1. **설정 → 프로바이더**로 이동
2. 프로바이더 선택
3. **API 키** 아래에서 **새 키 추가** 탭
4. **이름** (예: "업무용", "개인용", "팀 공유") 및 **키 값** 입력
5. **추가** 탭

라디오 버튼을 탭하여 활성 키 설정. 키를 길게 눌러 **편집** 또는 **삭제**.

### 키 안전

!!! warning
    API 키는 암호화된 Room 데이터베이스에 로컬로 저장됩니다. 橘子岛 서버로 전송되지 않습니다 (서버가 없습니다). 단, `.橘子岛` 내보내기 파일에 포함할 경우 평문으로 내보내집니다.

---

## 사용자 지정 프로바이더

모든 OpenAI 호환 API 엔드포인트 추가:

1. **설정 → 프로바이더**로 이동
2. 프로바이더 목록 하단의 **+ 사용자 지정 프로바이더 추가** 탭
3. 입력:
    - **프로바이더 이름** — 표시 이름
    - **기본 URL** — API 엔드포인트
4. **추가** 탭

橘子岛는 `{base_url}/v1/models`에서 모델 목록을 가져옵니다. 추가 후, 사용자 지정 프로바이더는 내장 프로바이더와 동일하게 작동합니다: API 키 추가, 모델 동기화, 채팅.

### 사용 사례

- **자체 호스팅** — vLLM, LocalAI, text-generation-webui 또는 기타 OpenAI 호환 서버에 연결
- **프록시** — 기업 프록시 또는 API 게이트웨이를 통한 라우팅
- **대체 엔드포인트** — Azure OpenAI, Cloudflare AI Gateway 또는 기타 호환 서비스 사용

### 이름 변경 또는 삭제

사용자 지정 프로바이더를 길게 눌러 **이름 변경** 또는 **삭제**. 삭제하면 프로바이더와 모든 키가 제거됩니다.

!!! warning
    내장 프로바이더는 이름 변경이나 삭제가 불가능합니다.

---

## 기본 URL 재정의

모든 프로바이더(내장 포함)는 편집 가능한 **기본 URL**을 가집니다. 다음에 유용합니다:

- **프록시**: `https://my-proxy.example.com/v1`로 라우팅
- **자체 호스팅**: 자체 인스턴스로 지정
- **지역 라우팅**: 지역별 엔드포인트 사용

---

## 모델 동기화

API 키 추가 후, 모델 목록 동기화:

1. **설정 → 모델**로 이동
2. **모든 프로바이더에서 동기화** 탭
3. 橘子岛가 구성된 모든 프로바이더의 사용 가능한 모델을 가져옵니다

스낵바에 동기화 진행 상황과 결과가 표시됩니다. 이후 개별 모델을 활성화/비활성화하고 기본값을 설정할 수 있습니다.

---

## 프로바이더별 참고 사항

### Google Gemini

- [Google AI Studio](https://aistudio.google.com/apikey)에서 API 키 발급
- 속도 제한이 있는 무료 티어 이용 가능
- 코드 실행 및 검색 그라운딩 지원 (내장 도구)

### OpenAI

- [Platform](https://platform.openai.com/api-keys)에서 API 키 발급
- 추론 모델 (o1, o3)은 특정 API 접근이 필요
- 스트리밍, 도구, 비전 모두 지원

### Anthropic

- [Console](https://console.anthropic.com/)에서 API 키 발급
- 설정 가능한 토큰 예산으로 확장 thinking
- 병렬 호출이 포함된 도구 사용 지원

### Ollama

- API 키 불필요 (로컬 네트워크)
- 기본 URL은 일반적으로 `http://<host>:11434/v1`
- Ollama API에서 모델 목록 가져옴
- Ollama 관련 문제 해결은 [FAQ](faq.ko.md) 참조

### OpenRouter

- 200개 이상의 모델에 단일 API 키
- 모델별 종량제 과금
- 개별 프로바이더 계정 없이 다양한 모델 시도에 적합

### 로컬 (llama.cpp)

- 네트워크 불필요
- GGUF 모델 파일이 기기에 저장됨
- 설정은 [로컬 모델](local-model.ko.md) 참조
