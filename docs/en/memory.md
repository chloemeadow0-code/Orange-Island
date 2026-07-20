# Memory & Cache

橘子岛 has a persistent memory system that lets the model remember information across conversations. Combined with automatic embedding-based caching, it provides a knowledge base that grows with your usage.

## Memory Types

### Active Memory

A single, always-on memory context that is included with **every API call** to the model. Think of it as a sticky note the model always sees.

**Use Active Memory for:**
- Your name, preferences, and background
- Project context the model should always know
- Standing instructions that apply to all conversations
- Facts you're tired of repeating

**Example Active Memory content:**
```text
User: Newo Ether
Preferences: Prefers Chinese for casual chat, English for technical topics.
Project: Building 橘子岛 — a BYOK Android LLM client.
Coding style: Kotlin, Jetpack Compose, MVVM architecture.
```

#### Editing Active Memory

1. Go to **Settings → Memory**
2. Scroll to **Active Memory**
3. Tap **Edit Active Memory**
4. Enter your content
5. Tap **Save**

The model can also update active memory via tool calls if **Access Active Memory** is enabled.

---

### Saved Memories

A collection of named memory files the model can search, read, create, edit, and delete. Unlike Active Memory (always sent), saved memories are retrieved on demand.

**Use Saved Memories for:**
- Reference material (API docs, config details, commands)
- Project-specific notes
- Learnings and insights from past conversations
- Anything you want the model to recall when relevant

#### Creating Memories Manually

1. Go to **Settings → Memory**
2. Tap **Add Memory**
3. Enter:
    - **Title** — descriptive name
    - **Description** — brief summary (used for search matching)
    - **Content** — the full memory content
4. Tap **Create**

#### Model-Created Memories

When **Access Saved Memories** is enabled, the model can create, read, update, and delete memory files via tool calls. This lets the model:

- Remember facts you tell it
- Save useful code snippets or configurations
- Build a knowledge base over time
- Clean up outdated information

---

## Memory Permissions

Control what the model can access:

| Setting | Location | When to Enable |
|---------|----------|----------------|
| **Access Saved Memories** | Settings → Memory | You want the model to read/write memory files |
| **Access Active Memory** | Settings → Memory | You want the model to update the persistent context |
| **Access Past Conversations** | Settings → Conversation Search | You want the model to search chat history |

All three default to **off**. Enable only what you need.

---

## Auto-Cache

Auto-caching automatically generates embeddings for new messages as they arrive. This keeps your conversation search index up to date without manual intervention.

### Enable Auto-Cache

1. Go to **Settings → Conversation Search**
2. Choose an embedding model (if you haven't already — see [Embedding / RAG](embedding.md))
3. Under **Caching**, toggle **Auto-cache new messages**

When enabled, every new message (user and model) is automatically embedded and indexed for semantic search.

### Manual Caching

If auto-cache is off, you can manually cache messages:

1. Go to **Settings → Conversation Search**
2. Tap **Cache** — computes embeddings for all uncached messages
3. Progress is shown as a circular indicator

Tap **Re-cache** to rebuild the entire index from scratch. This deletes all cached embeddings and re-processes every message. Use when:
- You've changed embedding models
- The cache seems corrupted or outdated
- Search results are unexpectedly poor

!!! warning
    Re-caching is irreversible and may take a while depending on your message count and embedding model speed.

### Cache Status

The embedding model settings show how many messages are cached vs. uncached:
- **"All N messages cached"** — up to date
- **"X of Y messages not cached"** — backlog to process

---

## Memory Tool Calls in Chat

When the model uses memory tools, you'll see inline cards:

| Tool | Card Text |
|------|-----------|
| Look up | "Looked through N saved memories" |
| Read | "Read [memory name]" |
| Save | "Saved [memory name]" |
| Edit | "Updated [memory name]" |
| Delete | "Removed [memory name]" |
| Update Active | "Updated active memory" |

Tap any card to see the full content that was read or written.

---

## Best Practices

- **Keep Active Memory concise** — it's included in every API call, so verbose content wastes tokens
- **Use descriptive titles for Saved Memories** — titles help the model find the right memory
- **Enable auto-cache** if you use conversation search regularly
- **Re-cache after switching embedding models** — different models produce incompatible embeddings
