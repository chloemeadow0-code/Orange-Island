# Generation Parameters

Control how models generate responses — from context length to creativity settings.

## Context Window

**Max Context Messages** sets how many recent messages are sent to the model as context. Default: **20**.

- **5–20** — Shorter context, faster responses, less token usage
- **20–50** — Longer context for complex, multi-turn conversations
- **50–100** — Maximal context for very long discussions (may hit token limits)

This applies to all models. The actual context window in tokens depends on your model and message length.

---

## Temperature

Controls randomness in model output. Range: **0.0 – 2.0**.

- **0.0 – 0.3** — More deterministic, consistent, factual
- **0.5 – 0.8** — Balanced creativity (recommended default)
- **1.0 – 2.0** — More random, creative, unpredictable

Higher temperature means the model is more likely to choose less probable words. Lower temperature produces more focused, repetitive outputs.

!!! tip "When to Adjust"
    - **Code / Facts**: Use low temperature (0.0 – 0.3)
    - **Creative Writing**: Use high temperature (0.8 – 1.2)
    - **General Chat**: Use medium temperature (0.5 – 0.7)

---

## Top P (Nucleus Sampling)

Controls the diversity of token selection. Range: **0.0 – 1.0**.

The model considers only the smallest set of tokens whose cumulative probability exceeds `top_p`.

- **0.1** — Very focused, only the most likely tokens
- **0.5** — Moderate diversity
- **0.9 – 1.0** — Full diversity (recommended default)

Usually you adjust *either* temperature *or* top P — not both.

---

## Default Max Tokens

Sets a maximum token limit for model responses. When set, the model will not generate more than this many tokens in a single response. When **not set** (default), the model's own maximum applies.

Available presets:

```
256   512   1024   2048
4096  8192  16384  32768
```

!!! tip "Leave Unset for Flexibility"
    For most use cases, leave this unset. Set a limit only when you need consistent response lengths (e.g., short summaries) or want to cap costs.

---

## Frequency Penalty

Reduces the model's tendency to repeat the same words. Range: **-2.0 – 2.0**.

- **Positive values** (0.1 – 1.0) — Discourage repetition
- **Zero** (0.0) — No penalty (default)
- **Negative values** (-1.0 – -0.1) — Encourage repetition

---

## Presence Penalty

Encourages the model to talk about new topics. Range: **-2.0 – 2.0**.

- **Positive values** (0.1 – 1.0) — Encourage topic diversity
- **Zero** (0.0) — No penalty (default)
- **Negative values** — Stay on current topic

---

## Thinking / Reasoning

Enables chain-of-thought reasoning for supported models (e.g., DeepSeek R1, Qwen3, Claude).

When enabled, the model generates internal reasoning before producing the final response. This improves accuracy for complex tasks but takes longer and uses more tokens.

### Thinking Level

- **Low** — Minimal reasoning, faster
- **Medium** — Balanced (default)
- **High** — Maximum reasoning for complex problems

!!! warning "Not All Models Support Thinking"
    Thinking mode requires a model that supports reasoning tokens. If your model doesn't support it, this setting has no effect.

---

## Visualize Context Rollout

When enabled, 橘子岛 visually indicates which messages are included in the current context window vs. which have been rolled out (excluded due to the context window limit). This helps you understand:

- How much of your conversation the model can "see"
- When older messages drop out of context
- Whether you need to increase the context window

The visualization appears as a subtle marker in the conversation view.

---

## How Parameters Work

All generation parameters are **nullable** — when not explicitly set, they are not sent to the model, and the model uses its own defaults. Each parameter has a reset option to clear the value back to "not set."

---

## Per-Conversation Overrides

You can override generation parameters for individual conversations using the **Advanced Settings** dialog in the chat screen (long-press the send button or use the ⋮ menu).
