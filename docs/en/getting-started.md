# Getting Started

This guide walks you through installing 橘子岛, adding your first API key, and sending your first message.

## Installation

### From F-Droid (Recommended)

橘子岛 is available on F-Droid, the open-source Android app store.

1. Install [F-Droid](https://f-droid.org/) on your device
2. Open F-Droid, search for **橘子岛**
3. Tap **Install**

### From GitHub Releases

1. Visit the [Releases page](https://github.com/orangeisland/app/releases)
2. Download the latest `.apk` file
3. Open the file on your device and confirm installation when prompted

### Build from Source

If you prefer building yourself:

1. Clone the repository:
   ```
   git clone https://github.com/orangeisland/app.git
   ```
2. Open the project in [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
3. Sync Gradle and build

Requirements: Android SDK 34+, JDK 17+.

---

## First Launch

When you open 橘子岛 for the first time, you'll see a welcome screen with a text input. Before you can chat, you need to configure a provider and an API key.

### Step 1: Add an API Key

1. Tap the **Settings** icon (bottom-right gear) in the navigation bar
2. Under **Services**, tap **Provider**
3. Select a provider from the list (e.g., **OpenAI**, **Anthropic**, **Google**)
4. Tap **Add New Key**
5. Enter a name for your key (e.g., "Personal") and paste your API key
6. Tap **Add**

??? tip "Where do I get an API key?"
    - **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — free tier available
    - **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic**: [Console API Keys](https://console.anthropic.com/)
    - **DeepSeek**: [Platform](https://platform.deepseek.com/)
    - **OpenRouter**: [Keys page](https://openrouter.ai/keys)

    See the [API Providers](provider.md) page for details on each provider.

### Step 2: Sync Models

1. Go back to Settings and tap **Models** (under **Services**)
2. Tap **Sync from All Providers**
3. 橘子岛 fetches the latest model list for all configured providers
4. Once synced, tap a model to set it as your **Default Model**

### Step 3: Send Your First Message

1. Tap the **back arrow** to return to the chat screen
2. Type a message in the input field at the bottom
3. Tap **Send** (paper plane icon)

The model will stream its response in real time.

---

## App Layout

橘子岛 has a clean layout centered around the chat screen:

### Top Bar

- **Conversation title** — displays the current conversation name (tap to rename)
- **Hamburger menu** (:material-menu:) — opens the conversations drawer
- **Overflow menu** (:material-dots-vertical:) — per-conversation settings (model, system prompt, generation params)

### Conversations Drawer

Tap the **hamburger menu** or swipe right from the left edge to open:

- **Search bar** — find past conversations by keyword or semantic search
- **Conversation list** — all conversations, newest first
- **Settings** (:material-cog:) — configure providers, models, prompts, and more
- **New Chat** — start a fresh conversation

### Chat Screen

- **Message area** — scrollable conversation history with markdown rendering
- **Bottom bar** — text input, model selector, attachment button (+), and send button

---

## Next Steps

- [Set up web search](web-search.md) — DuckDuckGo Lite works out of the box, no API key needed
- [Configure image generation](image-generation.md) — text-to-image right inside conversations
- [Set up auto backup](import-export.md#auto-backup) — hands-off periodic data protection
- [Explore agentic tools](tools.md) — shell execution, file operations, memory, and more
- [Import data](import-export.md) from Claude or ChatGPT
- [Run local models](local-model.md) for offline use
