# 橘子岛 Architecture

## 1. Architecture at a Glance

```
┌──────────────────────────────────────────────────────────────┐
│  UI Layer (Compose + Material 3)                              │
│  ChatApp → MessageList → MessageItem (+ RecomposeSafeMd)     │
│          → ChatBottomBar (+ AdvancedSettingsDialog)           │
│          → FullScreenMediaViewer / ZoomableImageItem          │
│          → SettingsScreen (tabs → 22 sub-pages)               │
│          → SettingsScaffold (collapsing large-title pattern)  │
├──────────────────────────────────────────────────────────────┤
│  ViewModel Layer                                              │
│  ChatViewModel (central orchestrator, ~2300 lines)            │
│  ├─ ConversationManager (tree traversal, branch switching)    │
│  ├─ GenerationManager (LLM calls, tool loop, ~1050 lines)     │
│  ├─ TranscriptionManager (image→text for blind providers)     │
│  ├─ RagManager (semantic/keyword search over messages)        │
│  ├─ ImageProcessor (image decode, resize, compress, PDF)      │
│  └─ delegate/SettingsDelegate (settings writes, ~700 lines)   │
├──────────────────────────────────────────────────────────────┤
│  Tool Layer (pluggable tool providers)                        │
│  ToolProvider → Memory / WebSearch / RAG / Shell / ImageGen  │
├──────────────────────────────────────────────────────────────┤
│  Repository Layer                                             │
│  ConversationRepository / SettingsRepository / MemoryRepository│
├──────────────────────────────────────────────────────────────┤
│  API Layer (8 built-in + custom providers)                    │
│  LlmProvider interface → Flow<StreamEvent>                    │
│  api/openai/    BaseOpenAiProvider + 5 subclasses             │
│  api/anthropic/ AnthropicProvider                             │
│  api/gemini/    GeminiProvider                                │
│  api/ollama/    OllamaProvider                                │
│  api/local/     LocalProvider (on-device GGUF via llama.cpp)  │
│  api/util/      MessageConverter, ThinkingParser, ToolMsgs    │
├──────────────────────────────────────────────────────────────┤
│  DI Layer (AppContainer)                                      │
│  Manual DI — creates DB, repos, managers once, reused         │
├──────────────────────────────────────────────────────────────┤
│  Data Layer                                                   │
│  Room DB v12 (conversations + messages + embeddings)          │
│  DataStore (settings, API keys, model lists, theming)          │
│  Filesystem (memory .md files, GGUF models, sandbox rootfs)   │
│  Export/Import (.橘子岛, Claude, ChatGPT formats)               │
│  AutoBackup (WorkManager periodic backup to .橘子岛)           │
├──────────────────────────────────────────────────────────────┤
│  Sandbox Layer (SAF + proot)                                  │
│  SandboxDocumentsProvider (Android Storage Access Framework)  │
│  SandboxManager (proot lifecycle, Alpine rootfs)              │
│  proot_jni.cpp (JNI bridge to PRoot chroot)                   │
├──────────────────────────────────────────────────────────────┤
│  Shell Layer (Conch Protocol)                                 │
│  ShellCrypto (ECDH + AES-256-GCM + HMAC-SHA256)              │
│  ShellClient (remote command + file I/O, glob, grep)          │
├──────────────────────────────────────────────────────────────┤
│  Native Layer (JNI via CMake)                                 │
│  llama_chat_jni.cpp (chat generation + modified-UTF-8 fix)    │
│  llama_jni.cpp (embeddings)                                   │
│  proot_jni.cpp (PRoot chroot sandbox)                         │
│  llama.cpp + proot (git submodules under thirdparty/)         │
└──────────────────────────────────────────────────────────────┘
```

**MVVM with delegates.** `ChatViewModel` owns all app state, delegating conversation logic to `ConversationManager`, settings writes to `SettingsDelegate`, and generation to `GenerationManager`. Created via `ChatViewModelFactory` which receives dependencies from `AppContainer`.

---

## 2. DI Layer

### AppContainer (`di/AppContainer.kt`, ~50 lines)

Manual DI container created in `MainActivity`. Creates all shared dependencies once:

```
AppContainer
├── settingsManager: SettingsManager
├── memoryManager: MemoryManager
├── secretCrypto: SecretCrypto
├── database: ChatDatabase → chatDao: ChatDao
├── conversationRepository: ConversationRepository
├── settingsRepository: SettingsRepository
├── memoryRepository: MemoryRepository
├── autoBackupManager: AutoBackupManager
├── sandboxManagerFactory: SandboxManagerFactory
└── chatViewModelFactory(): ChatViewModelFactory
```

All use `lazy` — created on first access, kept for app lifetime.

---

## 3. Data Layer

### 3a. Room Database (`data/local/ChatDatabase.kt`, 257 lines)

Three tables at version 12, with 10 incremental migrations (v2→v3 through v11→v12):

#### `conversations` table (`ChatEntity`)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (PK) | UUID |
| `title` | String | Display name |
| `lastUpdated` | Long | Sort order |
| `selectedBranchesJson` | String? | JSON map `{parentId: chosenChildId}` |
| `systemPromptId` | String? | Active system prompt |
| `modelId` | String? | Active model |

#### `messages` table (`MessageEntity`)

| Column | Type | Purpose |
|---|---|---|
| `id` | String (PK) | UUID (tool/result messages prefixed `tool_`/`result_`) |
| `conversationId` | String (FK) | Parent conversation (CASCADE delete) |
| `parentId` | String? | Forms the **tree** structure |
| `text` | String | Message body |
| `images` | List\<String\> | Local paths to processed images |
| `thoughts` | String? | Aggregated thinking text |
| `thoughtTitle` | String? | Title from thinking (e.g. `**Analysis**`) |
| `tokenCount` | Int | Total tokens used |
| `status` | Enum | SENDING → THINKING → TOOL_CALLING → TRANSCRIBING → SUCCESS / STOPPED / ERROR |
| `participant` | Enum | USER / MODEL / ERROR |
| `timestamp` | Long | Creation time |
| `thoughtTimeMs` | Long? | Thinking duration |
| `modelName` | String? | Model that generated this message |
| `retryText` | String? | Retry progress text (e.g. "2/5") |
| `toolCallJson` | String? | JSON of `MessageSegment` list (thought + tool segments) |
| `attachmentMeta` | String? | JSON of `AttachmentMeta` |

#### `embeddings` table (`EmbeddingEntity`)

| Column | Type | Purpose |
|---|---|---|
| `id` | Long (PK, auto) | Auto-increment |
| `messageId` | String | FK to messages |
| `modelId` | String | Embedding model ID |
| `embedding` | ByteArray | Big-endian float32 vector |
| `chunkText` | String | First 500 chars of source (preview) |
| `dimension` | Int | Vector dimension |

Unique constraint on `(messageId, modelId)`.

**Migrations at a glance:**
| Version | Change |
|---|---|
| 2→3 | `selectedBranchesJson` on conversations |
| 3→4 | `thoughtTimeMs` on messages |
| 4→5 | `modelName` on messages |
| 5→6 | `systemPromptId` on conversations |
| 6→7 | `modelId` on conversations |
| 7→8 | `thoughtTitle` on messages |
| 8→9 | `toolCallJson` on messages |
| 9→10 | `embeddings` table |
| 10→11 | `modelId` on embeddings, unique index `(messageId, modelId)` |
| 11→12 | `attachmentMeta` on messages |

`MessageConverters` handles type conversion for `Participant`, `MessageStatus`, `List<String>`.

### 3b. Repositories (`data/repository/`)

Thin wrappers around DAO and managers, introduced to decouple ViewModel from storage details:

| Repository | Lines | Wraps |
|---|---|---|
| `ConversationRepository` | 118 | ChatDao (conversation CRUD, message tree, branch selection, stuck-message repair, embedding cascading) |
| `SettingsRepository` | 142 | SettingsManager (typed read/write for all settings, provider management) |
| `MemoryRepository` | 29 | MemoryManager (file CRUD with path traversal protection) |

### 3c. DataStore Settings (`data/SettingsManager.kt`, 463 lines)

Persists to `settings` DataStore preferences. Stores:

- `selectedModel` — default model
- `availableModels` — JSON map `{"Google": ["Google:gemini-2.5-flash", ...], ...}`
- `enabledModels` — user-toggled models
- `modelAliases` — custom display names
- `apiKeys` — list of `ApiKeyEntry` (id, name, key, provider)
- `activeApiKeyIds` — selected key per provider
- `customProviders` — user-defined `CustomProviderEntry`
- `systemPrompts` — list of `SystemPromptEntry` with three-section editor
- `activeSystemPromptId` — global default
- `maxContextWindow` — messages to send (default 20)
- `visualizeContextRollout` — dim out-of-context messages
- `codeExecutionEnabled` / `googleSearchEnabled` / `thinkingEnabled`
- `thinkingLevel` — "low" / "medium" / "high"
- `providerBaseUrls` — custom base URLs per provider
- `titleGenerationEnabled` / `titleGenerationModel`
- `accessPastConversations` / `accessSavedMemories` / `accessActiveMemory`
- `ragSearchEnabled` / `modelSearchMethod` / `manualSearchMethod` / `ragThreshold`
- `embeddingModels` / `activeEmbeddingModelId`
- `autoCacheEnabled` — auto-cache new message embeddings
- `searchContextWindow` / `searchMatchLimit`
- `localChatModels` / `activeLocalChatModelId`
- `appLanguage` — UI language override
- `webSearchEnabled` / `webSearchProvider` / `webSearchApiKeysJson` / `webSearchBaseUrl` / `webSearchNumResults`
- `shellEnabled` / `shellDevicesJson`
- `transcriptionEnabled` / `transcriptionQuality` / `transcriptionModel` / `transcriptionModelId` / `activeTranscriptionProviderId`
- `themeMode` / `colorScheme` / `dynamicColor` / `schemeStyle`
- `autoUpdateCheck` / `lastUpdateCheckTime`
- **Conversation overrides** — per-conversation: contextWindow, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, code/Google/thinking/web/shell/transcription toggles

All reads via `Flow`; all writes via `dataStore.edit {}`. ChatViewModel calls `.stateIn(viewModelScope)` on each flow.

### 3d. Memory Manager (`data/MemoryManager.kt`, 162 lines)

File-based memory manipulated via tool calls:
- **Active memory** (`active_memory.md`) — prepended to system prompt
- **Saved files** (`memory_db/*.md`) — model can create, read, edit, delete

Thread-safe via `@Synchronized`. Path traversal protected by canonical path checks.

### 3e. Data Export & Import

**DataExporter** (319 lines) — exports as `.橘子岛` ZIP: Conversations (JSON), Memories (markdown), System Prompts (JSON), Settings (JSON), API Keys (JSON). Selective export by category.

**DataImporter** (530 lines) — imports `.橘子岛` with three strategies: Merge, Replace, Skip.

**ImportStreams** (data/ImportStreams.kt) — streaming chat import to avoid OOM on large files. Reads JSON incrementally rather than loading entire file into memory.

**ExportExtraSettings** (117 lines) — extra metadata bundled with exports.

**Third-party importers:**
- `ClaudeChatImporter` (302 lines) — Claude `.zip` with conversations.json
- `GptChatImporter` (423 lines) — ChatGPT `.zip` with conversations.json

### 3f. Auto Backup (`data/AutoBackupManager.kt`)

Manages periodic auto-backup of user data to `.橘子岛` ZIP files:
- **Mutex-guarded** — companion object Mutex ensures only one backup runs at a time across the process
- **Selective categories** — conversations, memories, prompts, settings, API keys (user-configurable)
- **Auto-delete policy** — configurable retention (keep last N backups)
- **Custom directory** — user-specified backup directory
- **Scheduling** — via `AutoBackupWorker` (WorkManager `CoroutineWorker`), configurable period (daily/weekly/monthly)
- **Settings** — 7 preference keys in `SettingsManager` for backup configuration

### 3g. System Prompt System

**BuiltInPrompts** (`data/BuiltInPrompts.kt`) — built-in prompt templates shipped with the app. 4 categories (General, Coding, Creative, Analysis) with editable defaults.

**DefaultSystemPrompt** (`data/DefaultSystemPrompt.kt`) — the app's default system prompt. Uses variable substitution (`{sent_time}`, `{sent_date}`, `{current_date}`).

---

## 4. Model Layer

### ChatMessage (`model/ChatMessage.kt`, 54 lines)

Core data classes: `ChatMessage`, `ChatConversation`, `ToolCallData`, `MessageSegment`, `Participant`, `MessageStatus`.

### ModelId (`model/ModelId.kt`, 44 lines)

Typed wrapper for `"ProviderName:modelId"` format. Replaces ad-hoc `substringBefore(":")`/`substringAfter(":")` parsing across the codebase. Falls back to heuristics for legacy unprefixed IDs (e.g. `"gpt-4"` → OpenAI, `"claude-3-opus"` → Anthropic).

### AttachmentMeta (`model/AttachmentMeta.kt`, 30 lines)

Metadata for non-image attachments: fileName, mimeType, size, pageCount, imageIndex, textContent, warning.

---

## 5. The ViewModel Layer

### 5a. ChatViewModel (`viewmodel/ChatViewModel.kt`, 2266 lines)

Central orchestrator. Key responsibilities:

**Conversation management:**
- `createNewChat()` / `selectConversation(id)` / `deleteConversation(id)`
- `renameConversation(id, title)` / `generateTitle(conversationId)`
- Delegates tree traversal and branch switching to `ConversationManager`

**Message sending (3 entry points):**
- `sendMessage(text, images, attachmentMeta)` — new message
- `regenerate(messageId)` — new sibling replacing a response
- `editMessage(messageId, newText)` — edit user message, create branch

All three follow the same pattern:
1. Create placeholder model message in `_allMessages`
2. Set `_streamingMessage` = placeholder
3. Launch `GenerationManager.generate()` in `generationScope` (IO dispatcher)
4. Pass callbacks: `onStreamUpdate`, `onLoadingChange`, `onGeneratingIdChange`, `onStreamClear`

**The `messages` StateFlow** combines three flows:
1. `_allMessages` — all messages in conversation (from Room DB)
2. `_streamingMessage` — currently-streaming message (null when idle)
3. `_selectedChildren` — branch selection map

Walks the tree from root (parentId=null), following `_selectedChildren` to pick which child at each level. Streaming message overlays its DB counterpart. Synthetic `tool_`/`result_` messages hidden from display path.

**Delegates to:**
| Delegate | Purpose |
|---|---|
| `ConversationManager` | Tree traversal, branch switching, stuck-message repair |
| `SettingsDelegate` | All DataStore writes, provider management (~700 lines moved out) |
| `GenerationManager` | LLM calls, tool execution loop |
| `TranscriptionManager` | Image→text for providers without vision |
| `RagManager` | Semantic/keyword search over messages |
| `ModelSyncManager` | Fetching model lists from individual providers |

**Other responsibilities:**
- RAG/embedding management (index, cache, search, orphan cleanup)
- Local chat model CRUD (GGUF model management)
- Shell device CRUD
- Auto-syncing local models into `availableModels` and `modelAliases`
- LM Studio model discovery

### 5b. GenerationManager (`viewmodel/GenerationManager.kt`, 1027 lines)

The engine that talks to LLMs. Config via `GenerationConfig` (provider, model, API key, system prompt, toggles, conversation overrides) and `GenerationContext` (memory access, shell config, RAG settings, web search config, embedding params, transcription state).

**`generate()` function** — main pipeline:

```
1. Build message path from DB (walking parentId chain)
2. If regenerating: trim path to exclude the replaced message
3. Collect tool providers: memory, shell, web search, RAG, transcription
4. Start 橘子岛ForegroundService (prevents process death)
5. Call provider.generateResponse(currentPath, ProviderConfig) → Flow<StreamEvent>
6. Collect events:
   - TextChunk → accumulate into totalText
   - ThoughtChunk → accumulate into currentThoughtBuf + totalThoughts
   - ToolCallRequest / ToolCallsRequest → execute tool via ToolProvider, add to segments
   - UsageUpdate → track token count
   - Error → set error status
   - Retrying → track retry attempts
7. After each event: call onStreamUpdate() with live ChatMessage (throttled ~500ms)
8. If tool calls: enter multi-tool loop (unlimited rounds, bounded by coroutine liveness)
   - Persist tool_ and result_ messages to DB
   - Call provider.generateResponse() again with updated path
9. In finally: persist final message to DB, onStreamClear(), stop foreground service
```

### 5c. ViewModel Support Files

| File | Lines | Purpose |
|---|---|---|
| `ConversationManager` | 120 | Tree traversal, branch selection, stuck-message repair |
| `ConversationUiState` | 56 | UI state data classes for conversation view |
| `SettingsDelegate` | 364 | All settings write operations extracted from ChatViewModel |
| `TranscriptionManager` | 191 | Image transcription for vision-blind providers (OpenAI fallback) |
| `RagManager` | 110 | Semantic (cosine similarity) and keyword search over messages |
| `ModelSyncManager` | 64 | Fetching available models from each provider |
| `ImageProcessor` | 89 | Image decode, resize (~1024px max), JPEG re-compress, video frame extraction, PDF page rendering |

---

## 6. The Tool Layer (`tool/`)

Pluggable tool framework. Each tool category implements the `ToolProvider` interface:

```kotlin
interface ToolProvider {
    fun definitions(ctx: GenerationContext): List<ToolDefinition>
    suspend fun execute(name: String, arguments: String, ctx: GenerationContext): String
    fun handles(name: String): Boolean
}
```

| Provider | Lines | Tools |
|---|---|---|
| `MemoryToolProvider` | 221 | `list_memory_files`, `read_memory_file`, `create_memory_file`, `edit_memory_file`, `delete_memory_file`, `update_active_memory` |
| `WebSearchToolProvider` | 193 | `web_search`, `web_fetch` (Brave, Serper, Tavily, SearXNG, DuckDuckGo Lite) |
| `RagToolProvider` | 61 | `search_conversations` (semantic + keyword search) |
| `ShellToolProvider` | 431 | `execute_shell_command`, `list_shells`, plus remote file I/O (read/write/edit/glob/grep) |
| `ImageGenToolProvider` | 121 | `generate_image` (prompt, size) → OpenAI-compatible `/v1/images/generations` BYOK, renders inline |

`GenerationManager` collects tool providers in `buildToolProviders()`, calls `definitions(ctx)` on each to build the full tool list, and dispatches tool execution to the matching provider via `handles()` + `execute()`.

---

## 7. The API Provider Layer

### 7a. Interface & Types (`api/LlmProvider.kt`, 203 lines)

```kotlin
interface LlmProvider {
    val name: String
    val defaultBaseUrl: String
    fun generateResponse(messages: List<ChatMessage>, config: ProviderConfig): Flow<StreamEvent>
    suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String>
}
```

**`StreamEvent`** sealed class — universal output format:
- `TextChunk(text)` — response text delta
- `ThoughtChunk(thought, title?, signature?)` — reasoning/thinking delta
- `ToolCallRequest(id, name, arguments, signature?)` — single tool call
- `ToolCallsRequest(calls)` — multiple tool calls in one response
- `UsageUpdate(tokenCount, thoughtsTokenCount)` — token usage
- `Error(message)` — error
- `Retrying(attempt, maxAttempts)` — transient error retry notification

### 7b. BaseOpenAiProvider (`api/openai/BaseOpenAiProvider.kt`, 203 lines)

Template method pattern for 5 OpenAI-compatible providers. Handles:
- Building `OpenAiChatRequest` with common fields
- Opening HTTP connection, writing JSON body
- Reading SSE lines (`data: {...}`), parsing JSON, dispatching to `parseDeltaContent()`
- Accumulating tool calls in `pendingToolCalls` map
- Emitting `ToolCallRequest`/`ToolCallsRequest` on `finish_reason=tool_calls`
- Emitting `UsageUpdate` when usage data arrives
- Auto-retry on transient errors (401, 429, 5xx) with `Retrying` events
- Flushing `thinkParser` after SSE loop ends (fix for buffered `<think>` content)

Subclasses override: `parseDeltaContent()`, `customizeRequest()`, `getExtraHeaders()`, `transformSystemPrompt()`.

### 7c. Individual Providers

| Provider | Base | Lines | Key Differences |
|---|---|---|---|
| **OpenAI** (`api/openai/`) | BaseOpenAiProvider | 27 | `reasoningEffort` for o1/o3; parses `reasoningContent`; respects `thinkingEnabled` |
| **DeepSeek** (`api/openai/`) | BaseOpenAiProvider | 22 | Parses `reasoningContent`; respects `thinkingEnabled` |
| **Qwen** (`api/openai/`) | BaseOpenAiProvider | 22 | DashScope base URL; parses `reasoningContent` |
| **OpenRouter** (`api/openai/`) | BaseOpenAiProvider | 45 | Timestamp in system prompt; `reasoning`/`plugins` fields; referer/title headers |
| **CustomOpenAi** (`api/openai/`) | BaseOpenAiProvider | 23 | User-defined name + base URL; parses `reasoningContent` |
| **Anthropic** (`api/anthropic/`) | Direct `LlmProvider` | 413 | Custom SSE protocol (`event:`/`data:` lines); `content_block_start/delta/stop`; thinking via `budget_tokens`; tool_use/tool_result blocks; image support via base64 |
| **Gemini** (`api/gemini/`) | Direct `LlmProvider` | 476 | Google SSE `streamGenerateContent`; inline base64 images; `thought` flag; code execution + Google Search as provider-specific tools; Gemini 2.5 vs 3 thinking config |
| **Ollama** (`api/ollama/`) | Direct `LlmProvider` | 278 | Local server `/api/chat`; structured `thinking` field or `<think>` tag fallback; `/api/tags` for model list |
| **Local** (`api/local/`) | Direct `LlmProvider` | 258 | On-device GGUF via llama.cpp JNI; chat template or ChatML fallback; StreamingThinkTagParser for `<think>`; Mutex-guarded engine lifecycle; tool calls emitted as text (unsupported natively) |

### 7d. Message Conversion (`api/util/MessageConverter.kt`, 146 lines)

Transforms internal `ChatMessage` tree into OpenAI-format API messages:
- `tool_` → `assistant` role with `tool_calls` array + `reasoning_content`
- `result_` → `tool` role with matching `tool_call_id`
- Normal → text + base64-encoded images
- Tool call IDs are SHA-256 hashes of `toolName:arguments` — deterministic for multi-turn consistency

Anthropic, Gemini, Ollama, and Local each have their own conversion logic inline.

### 7e. HTTP Client (`api/HttpClient.kt`, 52 lines)

OkHttp wrapper: `streamPost()`, `post()`, `fetchModels()`. 30s connect/read/write timeouts.

### 7f. Support Utilities (`api/util/`)

| File | Lines | Purpose |
|---|---|---|
| `StreamingThinkTagParser` | 71 | Stateful buffer for `<think>...</think>` in streams. One-exit guard. |
| `ThinkingParser` | 145 | Structured thinking block parsing (Anthropic, Gemini, etc.) |
| `ToolMessages` | 126 | Tool call/result message construction helper functions |

### 7g. Embedding Client (`api/EmbeddingClient.kt`, 70 lines)

OpenAI-compatible embeddings API: `computeEmbedding()` (single) + `computeEmbeddings()` (batch). Manual JSON escaping.

### 7h. Error Types (`api/GenerationError.kt`, 85 lines)

Structured error type for generation failures with provider attribution.

---

## 8. The Service Layer

| Service | Lines | Purpose |
|---|---|---|
| `橘子岛ForegroundService` | 120 | Foreground notification keeps process alive during LLM generation |
| `AutoBackupWorker` | 89 | WorkManager `CoroutineWorker` — periodic auto-backup of conversations/memories/prompts/settings to `.橘子岛` ZIP. Configurable period, categories, and auto-delete policy. Uses `AutoBackupManager` mutex for cross-instance safety |
| `EmbeddingCacheWorker` | 154 | WorkManager worker — survives process death during embedding cache operations. Reports progress via `setProgress` |
| `AppForegroundTracker` | 5 | App lifecycle monitoring stub |

---

## 9. The Shell Layer (Conch Protocol)

### 9a. ShellCrypto (`util/ShellCrypto.kt`, 116 lines)

- **ECDH key exchange** using X25519
- **AES-256-GCM** symmetric encryption with random nonces
- **HMAC-SHA256** request signing
- HKDF key derivation with info string `"conch-橘子岛-v1"`
- Token bucket rate limiting with nonce-based anti-replay

### 9b. ShellClient (`util/ShellClient.kt`, 277 lines)

HTTP client for Conch servers:
- `fetchPublicKey()` — retrieves server's X25519 public key
- `executeCommand(command)` — remote shell execution
- `readFile(path)`, `writeFile(path, content)`, `editFile(path, oldStr, newStr)` — remote file I/O
- `glob(pattern)`, `grep(pattern)` — remote file search
- Auto key exchange on first connection, public key caching
- `lastError` property for error reporting to the model

---

## 10. The Sandbox Layer (SAF + proot)

### 10a. SandboxManager (`sandbox/SandboxManager.kt`, ~200 lines)

Manages a PRoot-based Alpine Linux sandbox for code execution:

- **Rootfs lifecycle** — extracts Alpine Linux rootfs tarball (downloaded at build time via `build:`) into app-private storage on first use
- **proot invocation** — launches `proot` binary via JNI, chroot into Alpine rootfs
- **Package management** — `apk add/remove` via the Alpine package manager
- **VPN warning** — detects VPN/tun interfaces that conflict with proot's networking (the rootfs bind-mount gets blocked), shows warning dialog
- **Dashboard** — web-based sandbox status and package management UI
- **Reset** — wipe and re-extract the rootfs

### 10b. SandboxManagerFactory (`sandbox/SandboxManagerFactory.kt`)

Creates `SandboxManager` instances scoped to a filesDir root. Injected via `AppContainer`.

### 10c. SandboxDocumentsProvider (`sandbox/SandboxDocumentsProvider.kt`)

Android **Storage Access Framework (SAF) DocumentsProvider** that exposes the sandbox rootfs filesystem to other apps. Declared in the manifest with a custom authority. Supports:
- **Root home** — SAF root defaults to `/root` inside the sandbox for intuitive navigation
- **File operations** — open, create, delete, rename, query child documents
- **MIME type detection** — based on file extension

### 10d. Proot JNI (`cpp/proot_jni.cpp`, ~65 lines)

JNI bridge to the PRoot binary (built from `thirdparty/proot` git submodule via GNUmakefile):

| JNI Function | Purpose |
|---|---|
| `nativeProotExec` | Executes a command inside the PRoot chroot environment |
| `nativeProotVersion` | Returns PRoot version string |

PRoot is compiled as a static binary (no external dependencies besides libc). The build produces `lib橘子岛_proot.so` which bundles the proot code.

---

## 11. The Native Layer (JNI — llama.cpp)

### 11a. Build System (`app/src/main/cpp/CMakeLists.txt`)

- llama.cpp built as static library from `thirdparty/llama.cpp` (git submodule)
- JNI wrapper built as shared library `橘子岛_llama`
- C++17, arm64-v8a only, links `llama` + `log`
- GGML_OPENMP forced OFF for Android compatibility

### 11b. Chat JNI (`llama_chat_jni.cpp`, ~370 lines)

Wraps llama.cpp chat generation:

| JNI Function | Purpose |
|---|---|
| `nativeChatLoadModel` | Loads GGUF, creates context with `n_batch = n_ctx`, sets abort callback |
| `nativeChatGetTemplate` | Returns model's Jinja chat template or null |
| `nativeChatApplyTemplate` | Calls `llama_chat_apply_template()` with retry on buffer overflow |
| `nativeChatGenerate` | Tokenization, prefill via `llama_batch_get_one`, sampler chain (min_p→top_p→temp→dist), generation loop with cancel check, context space check |
| `nativeChatReset` | Clears KV cache via `llama_memory_clear()` |
| `nativeChatFreeModel` | Frees model and context |
| `nativeChatCancel` | Sets volatile cancel flag checked by abort callback and generation loop |

Sampler chain: `min_p(0.05) → top_p(configurable) → temp(configurable) → dist(seed)`

### 11c. Embedding JNI (`llama_jni.cpp`, ~175 lines)

| JNI Function | Purpose |
|---|---|
| `nativeLoadModel` | Loads GGUF with `embeddings=true`, `pooling_type=MEAN`, `n_ctx=512`, `n_batch=512` |
| `nativeFreeModel` | Frees model and context |
| `nativeComputeEmbedding` | Tokenizes, creates batch manually, calls `llama_encode`/`llama_decode`, returns pooled embedding |
| `nativeGetEmbeddingDim` | Returns `llama_model_n_embd_out()` |

### 11d. Kotlin JNI Wrappers

**LlamaEngine** (`api/LlamaEngine.kt`, 57 lines) — embedding singleton. Single and batch embedding methods. Batch loads once, computes all embeddings, frees once.

**LlamaChatEngine** (`api/LlamaChatEngine.kt`, 193 lines) — per-instance chat model. Streams tokens via `callbackFlow` with `NativeChatCallback`. Supports cancel, reset context, apply template.

---

## 12. The UI Layer

### 12a. MainActivity (`MainActivity.kt`, 456 lines)

Entry point. Handles:
- Splash screen setup
- Notification channel creation + permission request
- Database version check (error dialog if stored version > current version)
- Creates `AppContainer` (all DI), sets up Compose tree: `橘子岛Theme` → `MainNavigation`

### 12b. ChatApp (`ui/chat/ChatApp.kt`, 1194 lines)

Main screen:
- **ModalNavigationDrawer** — chat history sidebar with new chat, conversation list (long-press rename/delete/generate title), settings button
- **Scaffold** — top bar (title, menu, system prompt selector, new chat button)
- **AnimatedContent** — "New Chat" welcome vs `MessageList`
- **MessageList** — LazyColumn of `MessageItem`
- **ChatBottomBar** — text input, image picker, model selector, tool toggles, send/stop
- **FullScreenMediaViewer** — gesture-driven (pinch-zoom, pan, double-tap, fling, rubber-band)

### 12c. MessageList (`ui/chat/MessageList.kt`, 119 lines)

`LazyColumn` rendering each visible message as `MessageItem`. Computes context window boundaries for rollout visualization.

### 12d. MessageItem (`ui/chat/MessageItem.kt`, 1717 lines)

Three visual modes:
- **USER**: Right-aligned bubble with text, images/attachments, copy/edit/info, branch switcher
- **MODEL**: Left-aligned with thought blocks (expandable), tool call blocks (expandable), markdown rendering with inline LaTeX via `RecomposeSafeMarkdown`, status indicators (SENDING spinner, THINKING dots, TOOL_CALLING, TRANSCRIBING, STOPPED badge, ERROR banner), context rollout dimming
- **ERROR**: Center error banner

Supports inline editing (triggers `editMessage()`), branch switching, streaming content with debounced re-rendering (~500ms).

### 12e. ChatBottomBar (`ui/chat/ChatBottomBar.kt`, 1044 lines)

Input area with:
- Expandable text field with custom scrollbar
- Image/file picker via `PickMultipleVisualMedia`
- Attachment thumbnails with preview
- Model selector dropdown
- Tools menu: Code Execution, Web Search, Thinking, Shell, Transcription toggles
- Advanced settings dialog (per-conversation generation overrides)
- Send FAB / Stop button

### 12f. Media & Attachment Viewers

| File | Lines | Purpose |
|---|---|---|
| `FullScreenMediaViewer` | 325 | Gesture media viewer (pinch-zoom, pan, double-tap, fling) |
| `ZoomableImageItem` | 277 | Inline zoomable image with `ImageActions` (long-press save/share) |
| `AttachmentThumbnail` | 186 | Thumbnail for non-image file attachments |
| `VideoPlayer` | 228 | Embedded video playback |
| `VideoSliceDialog` | 211 | Video frame selection |
| `PdfPageSelectDialog` | 203 | PDF page selection |
| `TextFileViewer` | 163 | Plain text file viewer |
| `PdfPageRenderer` (`util/`) | 75 | PDF page to bitmap renderer |

### 12g. Settings Screen

**SettingsScaffold** (`ui/settings/SettingsScaffold.kt`) — unified collapsing large-title scaffold used by all ~22 settings sub-pages. Two variants:
- `CollapsingSettingsScaffold` — for `Column`-based pages
- `CollapsingSettingsLazyScaffold` — for `LazyColumn`-based pages

Both implement an iOS-style collapsing large-title pattern: the title shrinks and docks beside the back button as content scrolls, using a derived scroll fraction with eased scale/translation. Animation spec shared via `SettingsAnimations.kt` (spring + scale + fade page transitions).

**SettingsScreen** (`ui/settings/SettingsScreen.kt`, 305 lines) — main settings with tabs and sub-page navigation. Sub-pages:

| Page | Lines | Purpose |
|---|---|---|
| `SettingsProviderPage` | 187 | Provider list overview |
| `SettingsProviderDetailPage` | 482 | API key CRUD, base URL per provider, custom providers, local GGUF management |
| `SettingsModelsPage` | 351 | Default model selector, sync, expandable per-provider model lists |
| `SettingsPromptsPage` | 190 | System prompt CRUD with `PromptSettingControls` |
| `SystemPromptEditorPage` | 478 | Full-page three-section editor (system + userPrepend + userPostpend) |
| `SettingsMemoryPage` | 372 | Active memory + saved memory files CRUD |
| `SettingsSearchPage` | 905 | RAG toggle, search method, embedding model management, RAG threshold, cache management |
| `SettingsWebSearchPage` | 278 | Web search toggle, provider (DuckDuckGo Lite/Brave/Serper/Tavily/SearXNG), API key/URL |
| `SettingsShellPage` | 314 | Conch shell device CRUD |
| `SettingsTranscriptionPage` | 305 | Image transcription toggle, quality, model selection |
| `SettingsGenerationPage` | 459 | Per-conversation generation defaults |
| `SettingsImageGenPage` | 218 | Image generation toggle, model, size, BYOK key/URL |
| `SettingsSandboxPage` | 180 | Alpine Linux sandbox management, package install, rootfs reset |
| `SettingsTitleGenPage` | 139 | Auto-title toggle + model |
| `SettingsLanguagePage` | 95 | Language selection |
| `SettingsAppearancePage` | 226 | Theme mode, color scheme, dynamic color, blur effects toggle |
| `SettingsDataControlPage` | 777 | Export/Import .橘子岛, Claude/ChatGPT import, auto-backup config |
| `SettingsClaudeImportPage` | 266 | Claude import wizard |
| `SettingsAboutPage` | 200 | App version, licenses, update checker, crash log viewer |

### 12h. UI Components

| File | Lines | Purpose |
|---|---|---|
| `LatexRenderer` | 293 | LaTeX math rendering via JLaTeXMath-Android. Also: `parseLatexSpans()`, `escapeDollarForMarkdown()` — robust inline math parsing with prose gate, escaped dollar, CJK veto |
| `TypewriterText` | 73 | Streaming text animation |
| `AnimatedBlobBackground` | 133 | Decorative animated gradient blobs |
| `GradientBlur` (`util/`) | 38 | Compose blur effect with gradient mask |
| `橘子岛Haptics` (`ui/common/`) | 52 | Haptic feedback utility: long-press, selection, success/error patterns. Used in drawer, image viewer, message actions |
| `ThinkingControlPanel` (`ui/common/`) | 215 | Reusable thinking level selector (low/medium/high) with per-model compatibility detection |
| `PromptSettingControls` (`ui/settings/`) | 128 | Reusable prompt template controls for settings pages |
| `RatingForm` | 218 | In-app rating/feedback |
| `RecomposeSafeMarkdown` | 93 | Double-buffered crossfade Composable preventing flash during streaming AST re-parse. Debounced + effort/budget mutual exclusion |
| `DocumentationFab` | 73 | FAB linking to documentation |

### 12i. Theme (`ui/theme/`)

| File | Lines | Purpose |
|---|---|---|
| `Color.kt` | 68 | Material You dynamic color + static schemes |
| `Theme.kt` | 39 | 橘子岛Theme (light/dark/system, configurable) |
| `Type.kt` | 129 | Typography scale + MonoFamily |

---

## 13. Key Data Flows

### Sending a message (end-to-end):

```
1. User types text + attachments, taps Send
2. ChatBottomBar calls viewModel.sendMessage(text, images, attachmentMeta)
3. ChatViewModel:
   a. If new chat: creates ChatEntity in Room
   b. Creates user MessageEntity in Room (status=SUCCESS)
   c. Creates placeholder model MessageEntity (status=SENDING)
   d. Sets _streamingMessage = placeholder
   e. Sets _isLoading = true
   f. Launches generationScope { generationManager.generate(...) }
4. GenerationManager:
   a. Builds message path from DB (walking parentId chain)
   b. Collects tool providers (memory, shell, web search, RAG, transcription)
   c. Starts foreground service
   d. Calls provider.generateResponse(path, config)
   e. Collects TextChunk, ThoughtChunk, etc.
   f. After each event (throttled ~500ms): onStreamUpdate() → _streamingMessage updates → UI recomposes
   g. If tool calls: dispatches to matching ToolProvider, persists tool_/result_ to DB, loops
   h. In finally: persists final message to DB, onStreamClear() → _streamingMessage = null
5. onMessagePersisted callback triggers indexMessageForRag() if RAG enabled
6. UI: MessageList observes messages StateFlow, shows streaming bubble with live text
```

### Branching (message tree):

```
Root (null)
├── User Msg A (id=1, parentId=null)
│   ├── Model Response X (id=2, parentId=1)     ← selectedChildren[1] = 2
│   └── Model Response Y (id=3, parentId=1)     ← regenerate created this sibling
├── User Msg B (id=4, parentId=2)               ← follows selected path
│   └── Model Response Z (id=5, parentId=4)
```

`_selectedChildren` = `{"1": "2", "4": "5"}` walks → [1, 2, 4, 5].
Switch branch at parentId=1 → `{"1": "3"}` walks → [1, 3] (branch B pruned).

### Tool call flow:

```
1. Model emits a tool_call (e.g. "read_memory_file")
2. Provider emits ToolCallRequest(id, "read_memory_file", {"name":"notes.md"})
3. GenerationManager:
   a. Finds matching ToolProvider via handles("read_memory_file") → MemoryToolProvider
   b. Calls provider.execute("read_memory_file", args, ctx) → reads file
   c. Creates MessageSegment(type="tool", toolName=..., toolResult=...)
   d. Adds to segments list (UI shows tool call block)
4. After stream ends: tool_ message persisted to DB, result_ message persisted
5. Multi-tool loop: re-calls provider.generateResponse() with updated path including tool results
6. Model sees tool result and continues response
```

### Embedding lifecycle:

```
CACHE: cacheMessagesForModel() →
  1. If re-cache: uncache model, delete all embeddings for model
  2. Iterate all indexable messages (user/model, non-blank)
  3. Batch: local models load once, process all; remote models batch 64 per API call
  4. Upsert embeddings, mark model as cached

INDEX (auto): onMessagePersisted →
  indexMessageForRag() → if RAG + autoCacheEnabled → compute + upsert single embedding

INDEX (manual): onMessagePersisted →
  indexMessageForRag() → if manual search → compute + upsert single embedding

SEARCH: semanticSearch() →
  1. Compute query embedding
  2. Load all embeddings for active model
  3. Cosine similarity, filter by ragThreshold, sort, limit
  4. Fetch corresponding MessageEntity by IDs

DELETE: deleteConversation() →
  deleteEmbeddingsByConversation → deleteMessagesByConversation → deleteConversation

ORPHAN: deleteOrphanedEmbeddings() →
  DELETE embeddings WHERE messageId NOT IN (SELECT id FROM messages)
  Called on startup + after conversation deletions
```

---

## 14. Key Design Decisions

- **No HTTP library wrapper** — OkHttp used directly. Connection pooling across all providers.
- **ViewModel delegates to sub-managers** — ChatViewModel delegates to ConversationManager, SettingsDelegate, GenerationManager, TranscriptionManager, etc. Keeps each file focused.
- **Manual DI via AppContainer** — lazy-created shared dependencies, no annotation processor overhead.
- **Repository layer** — thin wrappers around DAO/Manager, decouple ViewModel from storage.
- **ToolProvider interface** — pluggable tool architecture. Each tool category is a self-contained provider.
- **Message tree, not list** — `parentId` + `_selectedChildren` enables branching without data duplication.
- **Tool calls are local** — model manipulates memory files, searches conversations (RAG), searches web (DuckDuckGo Lite/Brave/Serper/Tavily/SearXNG), controls remote machines (Conch), generates images (BYOK), transcribes images.
- **Image generation is BYOK** — dedicated API key + base URL, decoupled from chat provider. Images render inline in chat + full-screen viewer.
- **SSE streaming everywhere** — all providers stream via Server-Sent Events.
- **Model IDs are prefixed** — format `ProviderName:model-id` (e.g. `OpenAI:gpt-4`). `ModelId` type provides canonical parsing.
- **Custom providers** — user-defined OpenAI-compatible endpoints.
- **DuckDuckGo Lite as default search** — anonymous, no-key web search provider; HTML scrape, best-effort.
- **SAF DocumentsProvider for sandbox** — Android Storage Access Framework exposes sandbox rootfs to other apps; root home at `/root`.
- **llama.cpp + proot as git submodules** — under `thirdparty/`, linked via CMake (`add_subdirectory`) and GNUmakefile respectively.
- **Separate JNI for embedding vs chat vs sandbox** — `llama_jni.cpp` for embeddings, `llama_chat_jni.cpp` for chat, `proot_jni.cpp` for sandbox.
- **Mutex-guarded engine lifecycle** — only one local model loaded at a time.
- **On-device inference on IO dispatcher** — cancel support via volatile flag + abort callback.
- **Foreground service** — keeps process alive during LLM generation.
- **WorkManager for embedding cache + auto backup** — survives process death during bulk caching; periodic auto-backup with configurable period/categories/retention.
- **Conch end-to-end encryption** — ECDH + AES-256-GCM + HMAC-SHA256, token bucket, nonce anti-replay.
- **SecretCrypto for API key storage** — AES-256-GCM encrypted API keys with Android Keystore-backed key wrapping, TOFU (Trust On First Use) for SSH host keys.
- **Selective data export** — user chooses categories, API key warnings.
- **Three-section system prompts** — system + userPrepend + userPostpend with variable substitution.
- **Transcription fallback** — in-house image→text pipeline for providers without native vision support.
- **Collapsing large-title settings** — iOS-style unified scaffold across all ~22 settings sub-pages, derived scroll fraction with eased scale/translation, shared animation spec.
- **Haptic feedback** — `橘子岛Haptics` utility for long-press, selection, and success/error patterns throughout the UI.
- **Streaming markdown optimization** — double-buffered crossfade (`RecomposeSafeMarkdown`), debounced recomposition, effort/budget mutual exclusion for LaTeX+markdown.
- **Reproducible F-Droid builds** — via `build-fdroid.ps1` under Arch WSL; binary artifacts (proot/talloc .so, Alpine rootfs) built in `build:` step to avoid `scanignore`.

---

## 15. File Index

### API Layer (22 files)
| File | Lines | Purpose |
|---|---|---|
| `api/LlmProvider.kt` | 203 | Interface + StreamEvent + request/response types |
| `api/GenerationError.kt` | 85 | Structured generation error types |
| `api/EmbeddingClient.kt` | 70 | OpenAI-compatible embeddings API |
| `api/HttpClient.kt` | 52 | OkHttp wrapper |
| `api/LlamaEngine.kt` | 57 | JNI wrapper for embedding models |
| `api/LlamaChatEngine.kt` | 193 | JNI wrapper for chat models |
| `api/openai/BaseOpenAiProvider.kt` | 203 | Template for OpenAI-compatible providers |
| `api/openai/OpenAiProvider.kt` | 27 | OpenAI (reasoning_effort, reasoning_content) |
| `api/openai/DeepSeekProvider.kt` | 22 | DeepSeek |
| `api/openai/QwenProvider.kt` | 22 | Qwen / DashScope |
| `api/openai/OpenRouterProvider.kt` | 45 | OpenRouter (plugins, reasoning, web search) |
| `api/openai/CustomOpenAiProvider.kt` | 23 | User-defined endpoints |
| `api/anthropic/AnthropicProvider.kt` | 413 | Anthropic Claude (SSE events, thinking, images) |
| `api/gemini/GeminiProvider.kt` | 476 | Google Gemini (code exec, search, thinking) |
| `api/ollama/OllamaProvider.kt` | 278 | Ollama local server |
| `api/local/LocalProvider.kt` | 258 | On-device GGUF via llama.cpp |
| `api/util/MessageConverter.kt` | 146 | ChatMessage → OpenAI format |
| `api/util/StreamingThinkTagParser.kt` | 71 | Streaming `<think>` parser |
| `api/util/ThinkingParser.kt` | 145 | Structured thinking block parser |
| `api/util/ToolMessages.kt` | 126 | Tool message construction helpers |

### Tool Layer (6 files)
| File | Lines | Purpose |
|---|---|---|
| `tool/ToolProvider.kt` | 18 | Interface for pluggable tool providers |
| `tool/MemoryToolProvider.kt` | 221 | Memory file CRUD tools |
| `tool/WebSearchToolProvider.kt` | 193 | Web search + fetch tools (DDG Lite, Brave, Serper, Tavily, SearXNG) |
| `tool/RagToolProvider.kt` | 61 | Conversation search tool |
| `tool/ShellToolProvider.kt` | 431 | Shell execution + remote file I/O tools |
| `tool/ImageGenToolProvider.kt` | 121 | Image generation via BYOK `/v1/images/generations` |

### ViewModel Layer (9 files)
| File | Lines | Purpose |
|---|---|---|
| `viewmodel/ChatViewModel.kt` | ~2300 | Central ViewModel (state + orchestration) |
| `viewmodel/GenerationManager.kt` | ~1050 | Generation engine (streaming, tools, RAG, shell, image gen) |
| `viewmodel/ConversationManager.kt` | 120 | Tree traversal, branch switching |
| `viewmodel/ConversationUiState.kt` | 56 | UI state data classes |
| `viewmodel/delegate/SettingsDelegate.kt` | 364 | Settings writes extracted from ChatViewModel |
| `viewmodel/TranscriptionManager.kt` | 191 | Image transcription for vision-blind providers |
| `viewmodel/RagManager.kt` | 110 | Semantic + keyword search |
| `viewmodel/ImageProcessor.kt` | 89 | Image decode, resize, compress, video frames, PDF pages |
| `viewmodel/ChatViewModelFactory.kt` | 23 | Manual DI factory |

### Data Layer (17 files)
| File | Lines | Purpose |
|---|---|---|
| `data/local/ChatDatabase.kt` | 257 | Room DB v12, 10 migrations, ChatDao |
| `data/SettingsManager.kt` | 463 | DataStore preferences (all settings) |
| `data/MemoryManager.kt` | 162 | File-based persistent memory |
| `data/AutoBackupManager.kt` | 280 | Periodic auto-backup engine |
| `data/repository/ConversationRepository.kt` | 118 | ChatDao wrapper (CRUD, tree, branches) |
| `data/repository/SettingsRepository.kt` | 142 | SettingsManager wrapper |
| `data/repository/MemoryRepository.kt` | 29 | MemoryManager wrapper |
| `data/DataExporter.kt` | 319 | .橘子岛 export with selective categories |
| `data/DataImporter.kt` | 530 | .橘子岛 import (merge/replace/skip) |
| `data/ImportStreams.kt` | 45 | Streaming JSON import to avoid OOM |
| `data/ExportExtraSettings.kt` | 117 | Extra export metadata |
| `data/ClaudeChatImporter.kt` | 302 | Claude export (.zip) import |
| `data/GptChatImporter.kt` | 423 | ChatGPT export (.zip) import |
| `data/BuiltInPrompts.kt` | 60 | Built-in prompt templates (4 categories) |
| `data/DefaultSystemPrompt.kt` | 30 | Default system prompt with variables |
| `data/EmbeddingIndexer.kt` | 28 | FloatArray↔ByteArray + cosine similarity |
| `data/EmbeddingModelConfig.kt` | 15 | Embedding model config data class |
| `data/LocalChatModelConfig.kt` | 15 | Local chat model config data class |
| `data/PromptTemplateItem.kt` | 44 | System prompt template items |

### DI Layer (1 file)
| File | Lines | Purpose |
|---|---|---|
| `di/AppContainer.kt` | ~50 | Manual DI container (includes SecretCrypto, AutoBackupManager, SandboxManagerFactory) |

### Model Layer (4 files)
| File | Lines | Purpose |
|---|---|---|
| `model/ChatMessage.kt` | 54 | Core data classes |
| `model/ModelId.kt` | 44 | Typed "Provider:modelId" wrapper |
| `model/ThinkingControl.kt` | 20 | Thinking effort level + per-model compatibility |
| `model/AttachmentMeta.kt` | 30 | Attachment metadata |

### Service Layer (4 files)
| File | Lines | Purpose |
|---|---|---|
| `service/橘子岛ForegroundService.kt` | 120 | Foreground service for generation |
| `service/AutoBackupWorker.kt` | 89 | WorkManager CoroutineWorker for periodic auto-backup |
| `service/EmbeddingCacheWorker.kt` | 154 | WorkManager worker for embedding cache |
| `service/AppForegroundTracker.kt` | 5 | Lifecycle monitoring stub |

### Sandbox Layer (3 files)
| File | Lines | Purpose |
|---|---|---|
| `sandbox/SandboxManager.kt` | ~200 | PRoot Alpine Linux sandbox lifecycle + package management |
| `sandbox/SandboxManagerFactory.kt` | 18 | SandboxManager factory per filesDir |
| `sandbox/SandboxDocumentsProvider.kt` | ~180 | SAF DocumentsProvider for sandbox filesystem access |

### Shell Layer (2 files)
| File | Lines | Purpose |
|---|---|---|
| `util/ShellCrypto.kt` | 116 | ECDH + AES-256-GCM + HMAC-SHA256 |
| `util/ShellClient.kt` | 277 | Conch protocol HTTP client |

### Native Layer (4 files)
| File | Lines | Purpose |
|---|---|---|
| `cpp/llama_chat_jni.cpp` | ~370 | Chat generation JNI (+ modified-UTF-8 fix) |
| `cpp/llama_jni.cpp` | 175 | Embedding JNI |
| `cpp/proot_jni.cpp` | ~65 | PRoot chroot sandbox JNI |
| `cpp/CMakeLists.txt` | 19 | CMake build config (橘子岛_llama + 橘子岛_proot) |

### UI Layer (31 files)
| File | Lines | Purpose |
|---|---|---|
| `MainActivity.kt` | 456 | Entry point, Compose tree, splash |
| `ui/chat/ChatApp.kt` | 1194 | Main screen composable |
| `ui/chat/MessageItem.kt` | 1717 | Chat bubble composable |
| `ui/chat/MessageList.kt` | 119 | LazyColumn message list |
| `ui/chat/ChatBottomBar.kt` | 1044 | Input bar + model selector |
| `ui/chat/RecomposeSafeMarkdown.kt` | 93 | Double-buffered crossfade markdown |
| `ui/chat/AdvancedSettingsDialog.kt` | 264 | Per-conversation overrides dialog |
| `ui/chat/VideoPlayer.kt` | 228 | Embedded video player |
| `ui/chat/VideoSliceDialog.kt` | 211 | Video frame selection |
| `ui/chat/AttachmentThumbnail.kt` | 186 | Non-image attachment thumbnail |
| `ui/chat/FullScreenMediaViewer.kt` | 325 | Gesture full-screen media viewer |
| `ui/chat/ZoomableImageItem.kt` | 277 | Inline zoomable image + ImageActions |
| `ui/chat/ImageActions.kt` | 72 | Long-press save/share for images |
| `ui/chat/PdfPageSelectDialog.kt` | 203 | PDF page selection |
| `ui/chat/TextFileViewer.kt` | 163 | Text file viewer |
| `ui/settings/SettingsScaffold.kt` | 180 | Collapsing large-title scaffold (2 variants) |
| `ui/settings/SettingsAnimations.kt` | 40 | Shared page transition animation spec |
| `ui/settings/SettingsScreen.kt` | 305 | Main settings with tabs |
| `ui/settings/SettingsProviderPage.kt` | 187 | Provider list overview |
| `ui/settings/SettingsProviderDetailPage.kt` | 482 | Provider detail + local models |
| `ui/settings/SettingsModelsPage.kt` | 351 | Model selection |
| `ui/settings/SettingsPromptsPage.kt` | 190 | System prompt CRUD |
| `ui/settings/PromptSettingControls.kt` | 128 | Reusable prompt controls |
| `ui/settings/SystemPromptEditorPage.kt` | 478 | Full-page three-section editor |
| `ui/settings/SettingsMemoryPage.kt` | 372 | Memory management |
| `ui/settings/SettingsSearchPage.kt` | 905 | Search + embedding settings |
| `ui/settings/SettingsWebSearchPage.kt` | 278 | Web search settings |
| `ui/settings/SettingsShellPage.kt` | 314 | Conch shell device management |
| `ui/settings/SettingsTranscriptionPage.kt` | 305 | Image transcription settings |
| `ui/settings/SettingsImageGenPage.kt` | 218 | Image generation settings |
| `ui/settings/SettingsSandboxPage.kt` | 180 | Alpine Linux sandbox management |
| `ui/settings/SettingsGenerationPage.kt` | 459 | Per-conversation defaults |
| `ui/settings/SettingsTitleGenPage.kt` | 139 | Title generation settings |
| `ui/settings/SettingsLanguagePage.kt` | 95 | Language selection |
| `ui/settings/SettingsAppearancePage.kt` | 226 | Theme mode, color scheme, blur toggle |
| `ui/settings/SettingsDataControlPage.kt` | 777 | Export/Import + auto-backup config |
| `ui/settings/SettingsClaudeImportPage.kt` | 266 | Claude import wizard |
| `ui/settings/SettingsAboutPage.kt` | 200 | Version, licenses, updates, crash log |
| `ui/settings/DocumentationFab.kt` | 73 | Documentation FAB |
| `ui/settings/RatingForm.kt` | 218 | In-app rating form |
| `ui/onboarding/WelcomeScreen.kt` | 616 | Welcome/onboarding |
| `ui/common/ThinkingControlPanel.kt` | 215 | Thinking level selector |
| `ui/common/橘子岛Haptics.kt` | 52 | Haptic feedback utility |
| `ui/components/LatexRenderer.kt` | 293 | LaTeX math + dollar escaping |
| `ui/components/TypewriterText.kt` | 73 | Streaming text animation |
| `ui/components/AnimatedBlobBackground.kt` | 133 | Animated gradient blobs |
| `ui/theme/Color.kt` | 68 | Dynamic color + static schemes |
| `ui/theme/Theme.kt` | 39 | 橘子岛 theme |
| `ui/theme/Type.kt` | 129 | Typography + MonoFamily |

### Utilities (13 files)
| File | Lines | Purpose |
|---|---|---|
| `util/Constants.kt` | 14 | Message prefix constants |
| `util/SearchResultFormatter.kt` | 174 | Web result formatting |
| `util/SnackbarEvent.kt` | 6 | Snackbar event type |
| `util/SecretCrypto.kt` | 82 | AES-256-GCM API key encryption with Keystore wrapping |
| `util/ShellCrypto.kt` | 116 | Conch encryption |
| `util/ShellClient.kt` | 277 | Conch HTTP client |
| `util/SshClient.kt` | 95 | SSH client with TOFU host key verification |
| `util/CrashReporter.kt` | 70 | Crash report capture + upload to crash.orangeisland.app |
| `util/FileValidator.kt` | 83 | File import validation |
| `util/UpdateChecker.kt` | 71 | GitHub releases update checker |
| `util/DebugLog.kt` | 18 | Debug logging utility |
| `util/PdfPageRenderer.kt` | 75 | PDF page to bitmap renderer |
| `util/GradientBlur.kt` | 38 | Compose blur with gradient mask |
| `util/NoOpBringIntoView.kt` | 21 | Compose bring-into-view workaround |
