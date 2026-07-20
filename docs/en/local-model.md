# Local Models

Run LLMs directly on your Android device using GGUF model files and llama.cpp. No network required, no API keys, fully private.

## How It Works

橘子岛 bundles llama.cpp via Android NDK (CMake). When you import a GGUF file, the model runs entirely on your device's CPU — no data leaves the device.

## Requirements

- **GGUF format** models only (the standard for llama.cpp)
- **Device memory**: The model must fit in available RAM. As a rule of thumb:
    - 1–3B parameter models: 4–6 GB RAM
    - 7–8B parameter models: 6–8 GB RAM
- **Storage**: GGUF files range from ~500 MB (quantized small models) to 5+ GB

!!! warning
    Local inference is CPU-intensive and much slower than cloud APIs. It's best for offline use, privacy-sensitive content, or experimentation — not for fast, high-volume chat.

---

## Importing a Chat Model

1. Download a GGUF model file to your device (see recommended sources below)
2. Go to **Settings → Provider**
3. Select **Local** as the provider
4. Tap **Import GGUF Model**
5. Select the `.gguf` file from your device
6. Configure the model:

| Parameter | Description | Example |
|-----------|-------------|---------|
| **Model ID** | Lowercase identifier, no spaces | `qwen3-8b` |
| **Alias** | Display name | `Qwen 3 8B` |
| **Context Size** | Maximum context window in tokens | `4096` |
| **Temperature** | Randomness (0.0–2.0) | `0.7` |
| **Top P** | Nucleus sampling threshold (0.0–1.0) | `0.9` |
| **Max Tokens** | Maximum generation length | `2048` |

7. Tap **Add**

The model is imported and ready to use immediately.

---

## Importing an Embedding Model

Embedding models are smaller and used for semantic search:

1. Go to **Settings → Conversation Search**
2. Tap **Add Local Model**
3. Select a `.gguf` embedding model file
4. Give it a name
5. Tap **Add**

See [Embedding / RAG](embedding.md) for search setup.

---

## Selecting the Active Model

After importing one or more models:

1. Go to **Settings → Provider → Local**
2. You'll see all imported models listed
3. Tap the **radio button** next to the model you want to use
4. The selected model becomes active when **Local** is chosen as the chat provider

---

## Managing Local Models

### Rename

Tap a model to change its alias or adjust parameters (temperature, context size, etc.).

### Delete

Long-press a model and tap **Delete**. This removes the model from 橘子岛 and deletes the GGUF file from storage.

---

## Recommended Models

### Chat Models

| Model | Size | RAM Needed | Notes |
|-------|------|------------|-------|
| Qwen 3 1.7B | ~1 GB | 3–4 GB | Good quality for its size |
| Llama 3.2 3B | ~2 GB | 4–5 GB | Solid all-around |
| Qwen 3 8B | ~5 GB | 7–8 GB | Best quality, high RAM |

### Embedding Models

| Model | Size | Notes |
|-------|------|-------|
| BGE Small EN v1.5 | ~130 MB | Good English embeddings |
| BGE Small ZH v1.5 | ~130 MB | Chinese-optimized |
| Nomic Embed Text v1.5 | ~270 MB | Good multilingual |

### Where to Get GGUF Files

- [Hugging Face](https://huggingface.co/models?library=gguf) — search for "GGUF"
- [bartowski's quantized models](https://huggingface.co/bartowski) — wide selection, well-organized

!!! tip
    Look for `Q4_K_M` quantization — it offers the best trade-off between quality and size for chat models.

---

## Performance Tips

- **Smaller context = faster**: Start with 2048 and increase only if needed
- **Lower quant = faster**: Q4_K_M is faster than Q6_K or Q8
- **Close other apps**: Local inference needs as much RAM as possible
- **Plug in**: Inference is CPU-intensive, and prolonged use drains the battery
