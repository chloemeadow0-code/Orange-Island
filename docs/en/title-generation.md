# Title Generation

Automatically generate conversation titles based on the first exchange.

## What It Does

When you start a new conversation, 橘子岛 can automatically generate a short, meaningful title based on your first message and the model's response. This replaces the generic "New Chat" title.

## Setup

1. Go to **Settings → Title Generation**
2. Toggle **Auto-generate titles**
3. Optionally choose a **Model** for title generation (uses the current conversation model by default)

!!! tip "Model Choice"
    Title generation uses very few tokens. You can use a cheap, fast model (like GPT-4o Mini or a local model) without affecting your conversation's quality.

## How It Works

1. You send your first message in a new conversation
2. The model responds (as usual)
3. After the response completes, 橘子岛 sends a separate, small request to generate a title
4. The generated title is saved and displayed in the conversation list

Title generation only runs once per conversation, on the first exchange.

## Title Generation Model

You can use a different model specifically for title generation:

- **Default** (no selection) — Uses the same model as the conversation
- **Specific model** — Always uses that model for all title generation, regardless of which model is used for the conversation

Using a dedicated fast model for titles can reduce latency and cost.
