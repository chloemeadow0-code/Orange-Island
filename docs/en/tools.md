# Agentic Tools

橘子岛's models can autonomously use tools — they decide what to search, execute, read, or remember without you needing to manually trigger each action. Tools operate in **multi-round loops**: the model can call a tool, read the result, then decide to call another tool or respond.

## How Tool Calling Works

1. You send a message
2. The model decides it needs external information or action
3. It emits a **tool call** — a structured request with a tool name and arguments
4. 橘子岛 executes the tool on-device or on a remote server
5. The result is fed back to the model
6. The model can call another tool or produce a final response

This loop can repeat multiple times within a single message turn.

## Available Tools

### Web Search

Search the internet and fetch web pages. The model can look up current information, verify facts, or retrieve documentation.

- **Providers**: DuckDuckGo Lite (default, no key), Brave, Serper, Tavily, SearXNG
- **Configuration**: Settings → Web Search
- **Guide**: [Web Search](web-search.md)

### Image Generation

Generate images from text prompts using a dedicated text-to-image model. Images render inline in the conversation and can be viewed full-screen.

- **Provider**: BYOK — uses your own API key and base URL, decoupled from the chat model
- **Configuration**: Settings → Image Generation
- **Guide**: [Image Generation](image-generation.md)

### Code Execution

Execute code in an isolated environment:

- **Gemini Code Execution** — built-in code execution for Gemini models (no setup)
- **Sandbox** — local Alpine Linux environment via PRoot, with package management and SAF file access

### Remote Shell

Execute commands on remote machines via the [Conch](https://github.com/orangeisland/conch) protocol. The model can check server status, manage files, or run scripts.

- **Protocol**: End-to-end encrypted (ECDH + AES-256-GCM)
- **Configuration**: Settings → Shell
- **Guide**: [Remote Shell](shell.md)

### File Operations

Read, write, edit, glob-search, and grep-search files on remote devices through the Conch protocol. The model can directly manipulate remote filesystems.

!!! note
    File operations require a configured Conch shell device. See [Remote Shell](shell.md) for setup.

### Memory

Persistent knowledge storage that spans conversations:

- **Active Memory** — always included in every API call. Use for facts, preferences, or context the model should always remember.
- **Saved Memories** — a collection of named memory files the model can search, read, write, and edit via tool calls.

See [Memory & Cache](memory.md) for details.

### Conversation Search

The model can search your past conversation history using keyword or semantic (RAG) methods. This lets it reference previous discussions without you needing to manually find and share them.

See [Conversation Search](search.md) for setup.

---

## Tool UI in Chat

When a tool is called, you'll see it inline in the conversation:

<div class="grid cards" markdown>

- **:material-progress-wrench: Tool Call Banner**

    ---

    Shows the tool name and brief status (e.g., :material-magnify: "Searching 'latest AI news' on the web").

- **:material-check-circle: Tool Result**

    ---

    After execution, shows the formatted result or summary (e.g., "Found 5 results for 'latest AI news'").

</div>

### Expandable Details

Tap a tool call to expand it and see:

- **Arguments** — the exact parameters sent to the tool
- **Result** — the raw output from tool execution
- **Status** — success, failure, or partial results

### Failed Calls

If a tool call fails, the model is notified of the error and can retry or adjust. You'll see a red banner with the error message.

---

## Tool Permissions

You control which tools the model can access:

| Setting | Location | Default |
|---------|----------|---------|
| Web Search | Settings → Web Search | Off |
| Shell | Settings → Shell | Off |
| Memory (Saved) | Settings → Memory → Access Saved Memories | Off |
| Memory (Active) | Settings → Memory → Access Active Memory | Off |
| Past Conversations | Settings → Memory → Access Past Conversations | Off |
| Conversation Search | Settings → Conversation Search | On* |

*The model's ability to search conversations depends on having an embedding model configured. Without one, only keyword search is available.

---

## Multi-Round Tool Loops

The model can chain multiple tool calls. For example:

1. User: "What's the latest Linux kernel version and is my server running it?"
2. Model calls `web_search("latest Linux kernel version")`
3. Model calls `shell_execute("uname -r", device="my-server")`
4. Model compares results and responds

Each tool call and its result appear as separate inline items in the conversation before the final text response.
