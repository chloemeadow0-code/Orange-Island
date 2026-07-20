# System Prompts

System prompts define the model's persona, behavior, and ground rules. 橘子岛 gives you fine-grained control over how instructions are assembled and sent to the model.

## Three-Section Editor

Each system prompt template has three independently editable sections:

```text
┌─────────────────────────────────┐
│ System Prompt                   │ ← Core instructions (persona, rules, tone)
├─────────────────────────────────┤
│ User Prefix                     │ ← Prepended before every user message
├─────────────────────────────────┤
│ User Suffix                     │ ← Appended after every user message
└─────────────────────────────────┘
```

### System Prompt

The main instruction block. This is where you define:

- **Persona**: "You are a senior Python developer focused on clean architecture."
- **Rules**: "Always respond in Chinese. Use bullet points for lists."
- **Constraints**: "Never apologize. Be concise. Prefer code over explanation."

### User Prefix & Suffix

These wrap every message you send:

- **User Prefix** — added before your message text. Useful for reminders or context tags.
- **User Suffix** — added after your message text. Useful for closing instructions.

**Example**: If your prefix is `[Context: working on 橘子岛 docs]` and suffix is `\n\nPlease reply in Markdown.`, the model receives:

```text
[Context: working on 橘子岛 docs]
How do I configure web search?
Please reply in Markdown.
```

---

## Built-in Templates

橘子岛 ships with a library of ready-to-use prompt templates organized into 4 categories:

| Category | Description | Example |
|----------|-------------|---------|
| **General** | Versatile assistants for everyday tasks | Helpful assistant, explainer, summarizer |
| **Coding** | Software development and code review | Code reviewer, debugger, architect |
| **Creative** | Writing, storytelling, and ideation | Storyteller, poet, brainstorming partner |
| **Analysis** | Data analysis, research, and critique | Data analyst, researcher, devil's advocate |

Built-in templates can be used as-is or edited to suit your needs. They serve as starting points — customize the three sections to match your workflow.

---

## Creating a Prompt

1. Go to **Settings → System Prompts**
2. Tap **Add New Prompt**
3. Enter a **title** (e.g., "Translator", "Code Reviewer", "Chinese Assistant")
4. Fill in the three sections:
    - Tap **Add Text** to write static content
    - Tap **Add Variable** to insert dynamic values
5. Tap **Save**

### Reordering Items

Within each section, you can have multiple text blocks and variables. Long-press an item to:

- **Move up** / **Move down** — reorder within the section
- **Remove** — delete the item

---

## Variable Substitution

Variables are replaced with dynamic values when the message is sent:

| Variable | Expands to | Example | When Resolved |
|----------|-----------|---------|---------------|
| `{time}` | Current time (HH:mm:ss) | `14:30:00` | Prompt compilation |
| `{date}` | Current date (YYYY-MM-DD) | `2026-05-10` | Prompt compilation |
| `{sent_time}` | Message sent time (HH:mm) | `10:05` | Per-message |
| `{sent_date}` | Message sent date (YYYY-MM-DD) | `2026-05-11` | Per-message |
| `{active_memory}` | Content of the active memory | `[Your saved memory content]` | Prompt compilation |
| `{model_id}` | Currently selected model ID | `gemini-1.5-flash` | Prompt compilation |

**Per-message variables** (`{sent_time}`, `{sent_date}`) are resolved each time you send a message, so they reflect the exact send time. **Prompt-level variables** (`{time}`, `{date}`, `{active_memory}`, `{model_id}`) are resolved when the system prompt is compiled.

!!! tip
    Use `{sent_date}` for date-sensitive prompts like "Today is {sent_date}. When discussing recent events, note that your knowledge may be outdated." Use `{active_memory}` to inject the model's persistent memory into system instructions.

### Adding a Variable

1. In any section of the editor, tap **Add Variable**
2. Select the variable from the picker
3. It appears as a pill/chip in the section — drag to reposition

---

## Managing Prompts

### Set as Default

Tap the radio button next to a prompt to make it the **global default**. All conversations use this prompt unless overridden.

### Per-Conversation Override

Each conversation can use a different system prompt:

1. Open a conversation
2. Tap the overflow menu (:material-dots-vertical:) in the top bar
3. Select **Conversation Prompt**
4. Choose a prompt from the list

The per-conversation setting overrides the global default for that conversation only.

### Edit or Delete

- Tap a prompt to **edit** it
- Long-press and select **Delete** to remove it

!!! warning
    Deleting a system prompt is permanent. Conversations that used it will fall back to the global default.

---

## No System Prompt

If no system prompt is selected, the model receives no special instructions — it behaves according to its base training. This is sometimes desirable for testing or for models that perform better without system instructions.

To use no prompt, select **None** from the prompt list.

---

## Auto Title Generation

橘子岛 can automatically generate conversation titles after the first response:

1. Go to **Settings → Title Generation**
2. Enable **Auto-Generate Title**
3. Choose a **Title Model**:
    - **Use Current Model** — uses whatever model is active in the conversation
    - **Select Title Model** — pick a specific fast/cheap model for title generation

When enabled, a brief "Generating title..." snackbar appears after the first model response, and the conversation is automatically renamed from "Untitled" to a descriptive title.

---

## Prompt Examples

### Translator

```yaml
System Prompt: |
  You are a professional translator. Translate user input into English.
  Preserve formatting, code blocks, and technical terms. Do not add explanations.
```

### Code Reviewer

```yaml
System Prompt: |
  You are a senior code reviewer. When shown code:
  1. Identify bugs and edge cases
  2. Suggest performance improvements
  3. Check for security issues
  Be specific. Reference line numbers when possible.
```

### Chinese Assistant

```yaml
System Prompt: |
  你是一个乐于助人的中文助手。用简洁、清晰的中文回答问题。
User Suffix: |
  \n\n请用中文回答。
```
