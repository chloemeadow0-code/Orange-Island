# 橘子岛 User Manual

Welcome to the 橘子岛 user manual. 橘子岛 is a BYOK (Bring Your Own Key) LLM client for Android with multi-provider access, non-linear branching conversations, agentic tool calling, and remote device control.

## Quick Links

### Getting Started

- **[Getting Started](getting-started.md)** — install, configure, and send your first message
- **[FAQ](faq.md)** — answers to common questions

### Core Features

- **[Conversations](conversations.md)** — non-linear branching, message operations, streaming, markdown rendering
- **[API Providers](provider.md)** — connect to OpenAI, Anthropic, Google, DeepSeek, Ollama, and custom endpoints
- **[Models](models.md)** — enable/disable models, aliases, per-provider model sync
- **[System Prompts](system-prompts.md)** — three-section editor, variable substitution, per-conversation switching
- **[Generation](generation.md)** — temperature, top P, max tokens, thinking, frequency/presence penalties
- **[Title Generation](title-generation.md)** — auto-generate conversation titles
- **[Image Transcription](transcription.md)** — image-to-text pipeline for vision-blind providers
- **[Image Generation](image-generation.md)** — text-to-image generation as a chat tool
- **[Appearance](appearance.md)** — theme mode, color scheme, dynamic color, scheme style, blur effects

### Agentic Tools

- **[Overview](tools.md)** — how multi-round tool calling works
- **[Web Search](web-search.md)** — DuckDuckGo Lite, Brave, Serper, Tavily, SearXNG integration
- **[Remote Shell (Conch)](shell.md)** — encrypted remote command execution, file operations, MCP integration
- **[Sandbox](sandbox.md)** — local Alpine Linux environment for isolated command execution

### Knowledge Management

- **[Conversation Search](search.md)** — keyword and semantic (RAG) search over chat history
- **[Embedding / RAG](embedding.md)** — configure embedding models for semantic retrieval
- **[Memory & Cache](memory.md)** — active memory, saved memories, auto-caching

### More

- **[Local Models](local-model.md)** — run GGUF models on-device via llama.cpp
- **[PDF Import](pdf-import.md)** — extract and send PDF pages to vision models
- **[Data Portability](import-export.md)** — export/import .橘子岛 files, auto backup, import from Claude and ChatGPT
- **[Language](language.md)** — switch between English, 中文, 繁體中文, or system default
- **[About](about.md)** — version info, updates, documentation toggles, links, rating

---

## About 橘子岛

橘子岛 is a BYOK Android client for AI power users:

- **No middlemen**: Direct API connections, no telemetry, no tracking
- **On-device storage**: Everything lives locally in a Room database
- **Non-linear conversations**: Edit any past message and explore alternative branches
- **Agentic by default**: Multi-round tool calling with web search, image generation, code execution, shell, file operations, and memory
- **Remote control**: Manage servers via the encrypted Conch protocol
- **Open source**: MIT licensed, [source on GitHub](https://github.com/chloemeadow0-code/Orange-Island)
