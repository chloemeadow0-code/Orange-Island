# Conversations

橘子岛's conversation system is built around **non-linear branching** — unlike most chat apps, you can edit any past message and explore alternative response paths without losing the original conversation.

## Creating Conversations

Tap **New Chat** in the conversations drawer, or simply start typing in the chat screen. A new conversation is created automatically with your first message.

Conversations are auto-titled after the first response (if [Title Generation](system-prompts.md#auto-title-generation) is enabled), or you can rename them manually.

## Managing Conversations

### Switch Conversations

Open the **conversations drawer** (hamburger menu :material-menu: or swipe right) and tap any conversation to open it.

### Rename

1. Long-press a conversation in the drawer
2. Tap **Rename**
3. Enter a new title and save

### Delete

1. Long-press a conversation in the drawer
2. Tap **Delete**
3. Confirm the deletion — this action cannot be undone

---

## Non-Linear Branching

This is 橘子岛's signature feature. Every message can be a branching point.

### Editing a Past Message

1. Long-press any message bubble (user or model)
2. Tap **Edit**
3. Modify the message content
4. Send — 橘子岛 creates a **new branch** from this point

The original branch is preserved. You can switch between branches at any time.

### How Branches Work

Each message lives in a **tree structure**:

```text
Message 1 (User)
├── Message 2 (Model) ← original response
└── Message 3 (Model) ← branch created after editing Message 1
    ├── Message 4 (User)
    └── ...
```

When you edit a message and regenerate, the new response becomes a sibling of the original — both exist under the same parent message.

### Switching Branches

When a message has multiple children (branches), the UI shows navigation controls to switch between them. You can explore alternative paths without losing context.

### Why Branch?

- **Explore alternatives** — ask the same question with different wording
- **A/B test prompts** — compare responses from different system prompts or models
- **Fix mistakes** — correct a typo in your question without losing the original thread
- **Iterate** — refine a prompt through multiple versions while keeping all attempts

---

## Message Operations

Long-press any message to access these actions:

| Action | Description |
|--------|-------------|
| **Copy** | Copy the message text to clipboard |
| **Edit** | Edit the message and create a branch |
| **Info** | View metadata: timestamp, model used, token count |
| **Save/Share** | Long-press images to save to gallery or share |
| **Delete** | Delete this message and all follow-up replies |

!!! warning "Deleting a Message"
    Deleting a message also removes all responses that follow it. This cannot be undone.

---

## The Bottom Bar

The chat input area provides quick access to essential controls:

### Model Selector

Tap the model name on the left side of the bottom bar to open the **model picker**. You can switch models at any time — even mid-conversation. Different messages in the same conversation can use different models.

### Attachments

Tap **+** (:material-plus:) to attach files:

- **Photos** — images from your gallery
- **Videos** — video files (with frame extraction support)
- **Files** — any file type, including PDFs

Supported image formats are sent directly to vision-capable models. PDF files open a page selection dialog.

### Sending

Type your message and tap **Send** (:material-send:). The model streams its response token-by-token.

---

## Streaming & Display

### Real-Time Streaming

Responses appear word-by-word as the model generates them. 橘子岛 auto-scrolls to keep the latest content visible. Tap the **scroll-to-bottom** button (appears when you scroll up) to jump back to the live response.

### Markdown Rendering

Model responses are rendered with full markdown support:

- **Headers**, **bold**, *italic*, `inline code`
- **Code blocks** with syntax highlighting (use ````` ``` `````)
- **Tables**, blockquotes, lists
- **LaTeX math** — inline `$E=mc^2$` and block `$$\int_a^b f(x)dx$$` with improved parsing (CJK text detection, escaped dollar handling)

Streaming markdown is rendered with a **double-buffered crossfade** technique — the UI smoothly transitions between render passes instead of flickering on each token, even during rapid streaming.

### Thinking Display

For models that support reasoning (OpenAI o-series, Anthropic extended thinking, Gemini thinking, DeepSeek-R1), the model's thought process is shown in **grouped, collapsible panels**:

- The panel shows "Thinking..." during the reasoning phase
- Once complete, it displays the thinking duration (e.g., "Thought for 12s")
- Tap to expand/collapse the thought content
- Multiple thinking blocks are grouped together for cleaner presentation
- Tool calls made during thinking are counted (e.g., "Thought for 8s, called 2 tools")

### Generated Images

When [Image Generation](image-generation.md) is enabled, generated images appear inline in the conversation as model message attachments. Tap an image to open it full-screen with gesture controls (pinch-zoom, pan, double-tap). Long-press to save or share.

---

## Per-Conversation Settings

Each conversation can override global defaults:

- **Model** — select a different model for this conversation
- **System Prompt** — use a different system instruction
- **Generation parameters** — temperature, max tokens, thinking level

These overrides are set from the conversation's overflow menu in the top bar.

---

## Context Window

橘子岛 tracks token usage in real time. When a conversation exceeds the model's context window, older messages are visually **dimmed** to indicate they are outside the active context. The model no longer "sees" dimmed messages, but they remain visible in your UI.

Adjust the context window size in **Settings → Generation → Context Window**.
