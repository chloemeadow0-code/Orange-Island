# Frequently Asked Questions

## API & Providers

### How do I get an API key?

- **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — free tier available
- **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
- **Anthropic**: [Console API Keys](https://console.anthropic.com/)
- **DeepSeek**: [Platform](https://platform.deepseek.com/)
- **OpenRouter**: [Keys page](https://openrouter.ai/keys)
- **Brave Search**: [Brave Search API](https://api.search.brave.com/)

### Can I use multiple API keys for the same provider?

Yes. Each provider supports multiple named keys. Tap the radio button to select the active key. Useful for rotating between work/personal keys or having a backup ready. See [API Providers](provider.md#api-keys).

### How do I add a custom provider?

Go to Settings → Provider → **+ Add Custom Provider**. Enter a name and base URL. Any OpenAI-compatible endpoint works. See [Custom Providers](provider.md#custom-providers).

---

## Local Models

### What GGUF models work?

橘子岛 supports GGUF format for both chat and embedding. Chat models should fit in device memory (1–8B parameters depending on RAM). Embedding models are much smaller (100–500 MB). See [Local Models](local-model.md).

### How do I run models offline?

Import a GGUF chat model via Settings → Provider → Local → **Import GGUF Model**. For fully offline semantic search, also import a GGUF embedding model. No network connection needed.

### Why is my local model so slow?

Local inference runs on your device's CPU. It's inherently slower than cloud APIs. Tips: use smaller models (1–3B parameters), lower quantization (Q4_K_M), shorter context windows, and close background apps.

---

## Embeddings & Search

### Why is my embedding model test failing?

Common causes:

- **Wrong model name** — check exact spelling, including Ollama tags (e.g., `qwen3-embedding:8b` not `qwen3-embedding`)
- **Wrong base URL** — ensure the endpoint supports `/v1/embeddings`
- **Missing API key** — some providers require authentication even for embeddings
- **Network** — check connectivity to the endpoint

### What's the difference between keyword and RAG search?

Keyword search matches exact text. RAG (semantic search) matches by meaning — "database setup" can find "Room configuration" even without shared words. RAG requires an embedding model and cached messages. See [Conversation Search](search.md).

### How do I use Ollama for embeddings?

1. Install Ollama on a machine
2. Pull an embedding model: `ollama pull qwen3-embedding:8b`
3. In 橘子岛, add a remote embedding model with the **Ollama** preset
4. Use `http://<host>:11434/v1` as base URL
5. Enter the exact model name including the tag (e.g., `qwen3-embedding:8b`)
6. Leave API key blank

---

## Memory

### What's the difference between Active Memory and Saved Memories?

**Active Memory** is a single persistent context included with every API call — the model always sees it. **Saved Memories** are a collection of named files the model searches and retrieves on demand. Use Active Memory for persistent facts; use Saved Memories for reference material. See [Memory & Cache](memory.md).

### Can the model modify my memories?

Yes, if you enable **Access Saved Memories** and/or **Access Active Memory** in Settings → Memory. The model can create, read, edit, and delete memories via tool calls. All permissions default to off.

---

## Shell & Tools

### How do I set up remote shell access?

Deploy the [Conch](https://github.com/orangeisland/conch) server on your target machine, then add the device in Settings → Shell with its URL and API key. Both Conch and SSH devices are supported. See [Remote Shell](shell.md).

### Can I search the web without an API key?

Yes. **DuckDuckGo Lite** is the default web search provider and requires no API key. It works out of the box — just enable Web Search in Settings → Web Search. For higher reliability, configure one of the API-based providers (Brave, Serper, Tavily, SearXNG). See [Web Search](web-search.md).

### Is the shell connection encrypted?

Yes. Conch uses ECDH key exchange + AES-256-GCM encryption + HMAC-SHA256 signing. All traffic between 橘子岛 and the Conch server is end-to-end encrypted.

---

## Data

### How do I back up my data?

Go to Settings → Data Control → **Export Data** to create a manual `.橘子岛` backup. For hands-off protection, enable **Auto Backup** in Settings → Data Control → Auto Backup — it periodically backs up your data in the background. See [Data Portability](import-export.md).

### Can I import from ChatGPT or Claude?

Yes. Export your data from ChatGPT or Claude (they provide `.zip` files), then import in Settings → Data Control → **Third Party**. Both Merge and Replace strategies are supported. See [Data Portability](import-export.md#third-party-import).

### Are my API keys included in exports?

They can be, but it's optional. The export screen lets you toggle API key inclusion. A warning is shown when you enable it. Keys are stored in plain text within the `.橘子岛` file, so only include them for full device migrations to trusted destinations.

---

## General

### Where is my data stored?

Everything is stored locally on your Android device in a Room database. 橘子岛 has no servers, no cloud sync, no telemetry. Messages are sent directly from your device to the AI provider you configure.

### Does 橘子岛 support multiple languages?

Yes. The app UI supports **English**, **中文 (Chinese)**, and **繁體中文 (Traditional Chinese)**. Settings → Language. A restart is required after switching.

### How do I report a bug or request a feature?

Open an issue on [GitHub](https://github.com/orangeisland/app/issues). For contributions, see the [Contributing](https://github.com/orangeisland/app#contributing) section of the README.
