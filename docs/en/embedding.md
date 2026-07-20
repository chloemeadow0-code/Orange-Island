# Embedding / RAG

Embedding models convert text into numerical vectors that capture meaning. 橘子岛 uses these vectors for semantic search (RAG) over your conversation history — finding messages by what they mean, not just what words they contain.

## How It Works

1. Each message is sent to an embedding model
2. The model returns a vector (a list of numbers) representing the message's meaning
3. When you search, your query is also embedded
4. 橘子岛 computes **cosine similarity** between the query vector and all message vectors
5. Messages with similarity above your threshold are returned as matches

## Supported Providers

| Provider | Base URL | Requires API Key | Notes |
|----------|----------|------------------|-------|
| **OpenAI** | `https://api.openai.com/v1` | Yes | `text-embedding-3-small`, `text-embedding-3-large` |
| **Mistral** | `https://api.mistral.ai/v1` | Yes | `mistral-embed` |
| **Voyage AI** | `https://api.voyageai.com/v1` | Yes | `voyage-3`, `voyage-3-lite` |
| **SiliconFlow** | `https://api.siliconflow.cn/v1` | Yes | `BAAI/bge-large-zh-v1.5` (Chinese-optimized) |
| **Ollama** | `http://localhost:11434/v1` | No | `qwen3-embedding`, `nomic-embed-text`, etc. |
| **Custom** | Any | Optional | Any OpenAI-compatible embeddings endpoint |
| **Local** | N/A | No | GGUF embedding models via llama.cpp |

---

## Adding an Embedding Model

### Remote (API)

1. Go to **Settings → Conversation Search**
2. Tap **Add Remote Model**
3. Configure:

| Field | Description |
|-------|-------------|
| **Provider** | Select from the dropdown (OpenAI, Mistral, Voyage, SiliconFlow, Ollama, Custom) |
| **Model Name** | The exact model ID (e.g., `text-embedding-3-small`) |
| **Base URL** | Auto-filled for known providers; editable for proxies |
| **API Key** | Leave blank to auto-resolve from your chat provider key, or enter a dedicated key |
| **Batch Size** | Messages to embed per API request (1–100) |

4. Tap **Add** — a connection test runs before saving

!!! tip
    The API key field is optional if you've already configured the same provider for chat. Leave it blank and 橘子岛 resolves your chat API key automatically.

### Local (GGUF)

1. Go to **Settings → Conversation Search**
2. Tap **Add Local Model**
3. Import a `.gguf` embedding model file (e.g., `bge-small-en-v1.5-q4_k.gguf`)
4. Give it a name
5. Tap **Add**

Embedding models are typically much smaller than chat models — a few hundred MB at most.

### Ollama

1. Install Ollama on a machine
2. Pull an embedding model: `ollama pull qwen3-embedding:8b`
3. In 橘子岛, add a remote model:
    - Provider: **Ollama**
    - Base URL: `http://<host>:11434/v1`
    - Model name: `qwen3-embedding:8b` (include the `:tag`)
    - API key: leave blank
4. Tap **Add**

!!! note
    Ollama suffix tags like `:8b`, `:latest` are part of the model name. Use the exact name from `ollama list`.

---

## Caching

After adding a model, you need to cache your messages (generate embeddings):

1. Tap **Cache** on the embedding model
2. 橘子岛 processes all uncached messages in batches
3. A circular progress indicator shows the current progress
4. Completion: "All N messages cached"

### Auto-Cache

Enable **Auto-cache** to automatically embed new messages as they arrive. This keeps your search index always up to date.

### Re-Cache

Tap **Re-cache** to delete all existing embeddings and rebuild from scratch. Use when:

- Switching to a different embedding model
- Embedding quality seems degraded
- The cache is inconsistent

!!! warning
    Re-caching cannot be undone and may take a long time for large message histories.

---

## Batch Size

The **Batch Size** setting (1–100) controls how many messages are sent per API request during caching:

- **Higher**: Faster caching, but larger API payloads
- **Lower**: Smaller requests, slower but more reliable on slow connections

Start with the default and adjust if you encounter timeouts (lower it) or want faster caching (raise it).

---

## Testing Your Setup

When you add a remote model, 橘子岛 runs an automatic connection test. If it fails:

1. Check the model name — include tags for Ollama (`:8b`, `:latest`)
2. Verify the base URL is reachable from your device
3. Confirm the API key is valid (if required)
4. Try a known model name for that provider

Common errors:
- **"Wrong model name"** — check exact spelling, including tags
- **"Wrong base URL"** — ensure the endpoint supports `/v1/embeddings`
- **"Missing API key"** — some providers require authentication
- **"Network error"** — check connectivity

---

## Provider Recommendations

| Use Case | Recommended Provider |
|----------|---------------------|
| **Best quality (English)** | Voyage AI `voyage-3` |
| **Best quality (Chinese)** | SiliconFlow `BAAI/bge-large-zh-v1.5` |
| **Free / self-hosted** | Ollama `qwen3-embedding` or `nomic-embed-text` |
| **Fully offline** | Local GGUF `bge-small-en-v1.5` |
| **Already using OpenAI** | OpenAI `text-embedding-3-small` (cheap, fast) |
