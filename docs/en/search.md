# Conversation Search

橘子岛 can search your entire conversation history — either by keyword matching or semantic (meaning-based) retrieval using embedding models.

## Search Methods

### Keyword Search

Fast, exact text matching. The search looks for literal occurrences of your query in message content.

**Best for:**
- Finding a specific phrase or term
- Quick lookups when you remember exact wording
- Zero setup — works immediately

**Limitations:**
- Misses synonyms and related concepts
- No understanding of meaning

### Semantic Search (RAG)

Uses embedding models to find messages by **meaning**, not exact words. A query for "how to set up the database" can find messages about "Room configuration" even if the word "database" never appears.

**Best for:**
- Finding conversations by topic or theme
- Broad queries where you don't remember exact phrasing
- Discovering related discussions across different conversations

**Requirements:**
- An embedding model must be configured (see [Embedding / RAG](embedding.md))
- Messages must be cached (embeddings generated)

---

## Setting Up

### 1. Add an Embedding Model

See [Embedding / RAG](embedding.md) for detailed setup. You can use:
- **Remote models** (OpenAI, Mistral, Voyage, Ollama, etc.)
- **Local models** (GGUF files, fully offline)

### 2. Choose Search Methods

In **Settings → Conversation Search**:

| Setting | Description |
|---------|-------------|
| **Model Search Method** | How the model searches when it calls the `search_conversations` tool |
| **Manual Search Method** | How the search bar in the conversations drawer works |

Set each to **Keyword** or **Semantic (RAG)**.

### 3. Configure Search Scope

| Setting | Range | Description |
|---------|-------|-------------|
| **Context messages per search hit** | 4–32 | How many surrounding messages to include with each match (steps: 4, 8, 12, 16, 20, 24, 28, 32) |
| **Max search results** | 5–30 | Maximum number of matches to return (steps: 5, 10, 15, 20, 25, 30) |
| **Similarity Threshold** | 0.0–1.0 | RAG only: minimum similarity score for a match. Higher = stricter. Default: 0.5 |

### 4. Cache Messages

If using RAG, tap **Cache** to generate embeddings for all existing messages. Enable **Auto-cache** to keep the index updated automatically.

---

## Using Search

### Manual Search (Search Bar)

1. Open the **conversations drawer** (hamburger menu :material-menu: or swipe right)
2. Tap the search bar at the top
3. Type your query
4. Results appear below — tap any result to open that conversation at the matching message

### Model-Initiated Search

When **Access Past Conversations** is enabled (Settings → Memory), the model can search your history autonomously:

```text
You: "What did we decide about the API design last week?"
Model: [Searches "API design decision"]
       "Last Tuesday, we decided to use..."
```

The search appears as a tool card in the conversation.

---

## Similarity Threshold

The **Similarity Threshold** slider (0.0 to 1.0) controls how closely a message must match to be included in RAG results:

- **Low (0.3–0.5)**: More results, may include loosely related content
- **Medium (0.5–0.7)**: Balanced — good default
- **High (0.7–0.9)**: Fewer results, only very close matches

Start with the default and adjust based on your results. If you get too many irrelevant matches, raise the threshold. If you miss relevant conversations, lower it.

---

## Search Results Display

In the conversations drawer, search results show:

- **Conversation title** (or "Untitled")
- **Matching message** — the user or model message that matched
- **Role label** — User or Model
- **Context messages** — surrounding messages for context

Tap a result to open the conversation scrolled to the matching message.
