# API Providers

橘子岛 connects directly to AI providers — no middleman, no subscription, no telemetry. You bring your own API keys, and everything runs from your device.

## Built-in Providers

| Provider | Base URL | Models | Notes |
|----------|----------|--------|-------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Gemini series | Free tier available via Google AI Studio |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4, GPT-4o, o-series | Reasoning models supported |
| **Anthropic** | `https://api.anthropic.com/v1` | Claude series | Extended thinking supported |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3, DeepSeek-R1 | Reasoning models supported |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Qwen series | Via Alibaba DashScope |
| **Ollama** | `http://localhost:11434/v1` | Any pulled model | Self-hosted, no API key needed |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Multi-provider | Access many models through one API |
| **Local** | N/A | GGUF models | On-device via llama.cpp, fully offline |

## Switching Providers

Tap the provider selector in Settings to switch between providers. Each provider maintains its own:

- API keys
- Base URL (editable for proxies/self-hosted)
- Model list

---

## API Keys

### Multiple Keys per Provider

Each provider supports multiple named API keys. This enables:

- **Rotation** — switch between keys for different usage tiers
- **Organization** — separate work and personal usage
- **Fallback** — keep a backup key ready

### Managing Keys

1. Go to **Settings → Provider**
2. Select a provider
3. Under **API Keys**, tap **Add New Key**
4. Enter a **name** (e.g., "Work", "Personal", "Team Shared") and the **key value**
5. Tap **Add**

Tap the radio button to set the active key. Long-press a key to **Edit** or **Delete**.

### Key Safety

!!! warning
    API keys are stored locally in an encrypted Room database. They are never sent to 橘子岛 servers (there are none). However, they are exported in plain text if you include them in a `.橘子岛` export file.

---

## Custom Providers

Add any OpenAI-compatible API endpoint:

1. Go to **Settings → Provider**
2. Tap **+ Add Custom Provider** at the bottom of the provider list
3. Enter:
    - **Provider Name** — any display name
    - **Base URL** — the API endpoint
4. Tap **Add**

橘子岛 fetches the model list from `{base_url}/v1/models`. Once added, custom providers work exactly like built-in ones: add API keys, sync models, and chat.

### Use Cases

- **Self-hosted** — connect to vLLM, LocalAI, text-generation-webui, or other OpenAI-compatible servers
- **Proxies** — route through a corporate proxy or API gateway
- **Alternative endpoints** — use Azure OpenAI, Cloudflare AI Gateway, or other compatible services

### Rename or Delete

Long-press a custom provider to **Rename** or **Delete**. Deleting removes the provider and all its keys.

!!! warning
    Built-in providers cannot be renamed or deleted.

---

## Base URL Override

Every provider (including built-in) has an editable **Base URL**. This is useful for:

- **Proxies**: Route through `https://my-proxy.example.com/v1`
- **Self-hosted**: Point to your own instance
- **Region routing**: Use region-specific endpoints

---

## Syncing Models

After adding API keys, sync the model list:

1. Go to **Settings → Models**
2. Tap **Sync from All Providers**
3. 橘子岛 fetches available models from every configured provider

A snackbar shows sync progress and results. You can then enable/disable individual models and set a default.

---

## Provider-Specific Notes

### Google Gemini

- API keys from [Google AI Studio](https://aistudio.google.com/apikey)
- Free tier available with rate limits
- Supports code execution and search grounding (built-in tools)

### OpenAI

- API keys from [Platform](https://platform.openai.com/api-keys)
- Reasoning models (o1, o3) require specific API access
- Streaming, tools, and vision all supported

### Anthropic

- API keys from [Console](https://console.anthropic.com/)
- Extended thinking with configurable token budgets
- Tool use with parallel calls supported

### Ollama

- No API key required (local network)
- Base URL typically `http://<host>:11434/v1`
- Model list fetched from Ollama's API
- See [FAQ](faq.md) for Ollama-specific troubleshooting

### OpenRouter

- Single API key for 200+ models
- Pay-per-token pricing varies by model
- Good for trying different models without individual provider accounts

### Local (llama.cpp)

- No network required
- GGUF model files stored on-device
- See [Local Models](local-model.md) for setup
