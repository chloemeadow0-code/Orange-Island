# Models

Manage which AI models are available and set your default model for conversations.

## Model List

The **Models** page shows all models 橘子岛 knows about, organized by provider:

- **Default Model** — The model used for new conversations. Tap to change.
- **Available Models** — Expand each provider to see its models. Enable the ones you want to use.

### Enable / Disable Models

Check or uncheck the checkbox next to a model to toggle its availability. Disabled models won't appear in the model picker in conversations.

### Rename Models

Tap the edit icon (pen) next to a model to give it a custom alias. This alias appears throughout the app instead of the technical model ID.

### Sync Models

Tap **Sync Models** to fetch the latest available models from all configured API providers. This requires an internet connection and valid API keys.

!!! tip "Local Models"
    Local models appear under the **Local** provider section. They are managed separately in **Settings → Providers → Local**.

---

## Default Model

The **Default Model** is used for all new conversations. To change it:

1. Tap the default model row at the top of the Models page
2. Select a model from the list (only enabled models are shown)
3. The change takes effect immediately

You can override the model per-conversation from the chat screen's model picker.

---

## Model Aliases

Model aliases let you give friendly names to models with long technical IDs. For example, you might rename `openai/gpt-4o-mini` to just "GPT-4o Mini".

Aliases are shown everywhere: the model picker, conversation headers, and settings pages.

To remove an alias, clear the text field and save.

---

## Troubleshooting

### Models not appearing

- Tap **Sync Models** to refresh the list
- Verify that you have a valid API key for the provider in **Settings → Providers**
- Check your internet connection
- Some providers may be temporarily unavailable

### Local models not shown

- Import a GGUF model file in **Settings → Providers → Local**
- The model must be in valid GGUF format
