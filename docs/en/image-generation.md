# Image Generation

Generate images from text prompts using a text-to-image model, right inside your conversations.

## What It Does

When image generation is enabled, 橘子岛 can turn your prompts into images using a dedicated text-to-image model (such as DALL·E, GPT-Image, Imagen, FLUX, Stable Diffusion, Seedream, Qwen-Image, and many others). The generated image is returned into the conversation, so you can iterate on it just like any other reply.

Image generation uses its **own model selection**, independent of the model you chat with — so you can chat with one model and generate images with another.

## Setup

1. Go to **Settings → Image Generation**
2. Toggle **Enable Image Generation** on
3. Tap **Model** and choose a text-to-image model
4. Optionally set the **Default Size** (width × height)

!!! note "BYOK — dedicated credentials"
    Image generation uses its **own dedicated API key and base URL**, independent of your chat providers. This means you can use a different service for images than for chat. Go to **Settings → Image Generation** to configure the key, base URL, model, and default size.

## Model Selection

Tap **Model** to pick the model used for generation.

- The picker shows models that look like text-to-image models, filtered from all your synced models, so the list stays short.
- If the model you want isn't listed (an unusual name), enable **Show all models** to pick from the full list.
- Only a properly synced `Provider:model` entry counts as a valid selection. Sync your models first under **Settings → API Providers** / **Manage Models** if the list is empty.

## Default Size

Sets the default output dimensions, entered as **width × height** in pixels (for example `1024` × `1024`).

- The default is `1024 × 1024`.
- Supported sizes depend on the model and provider — if a model rejects a size, try a value it documents (common options are `1024×1024`, `1024×1792`, `1792×1024`).

## How It Works

1. Enable image generation and select an image model
2. In a conversation, ask the assistant to create an image
3. 橘子岛 routes the request to the configured image model using that provider's credentials
4. The generated image is returned into the conversation

!!! tip
    Be specific in your prompt — describe the subject, style, composition, and lighting. Clear prompts produce far better results than vague ones.
