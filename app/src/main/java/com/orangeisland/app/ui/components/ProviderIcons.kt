package com.orangeisland.app.ui.components

import com.orangeisland.app.R

/**
 * Single source of truth mapping a built-in provider name to its brand icon drawable.
 * Returns 0 for unknown / custom providers (callers fall back to a generic Cloud icon).
 */
fun providerIcon(name: String): Int = when (name.lowercase()) {
    "google" -> R.drawable.provider_google
    "openai" -> R.drawable.provider_openai
    "anthropic" -> R.drawable.provider_anthropic
    "deepseek" -> R.drawable.provider_deepseek
    "qwen" -> R.drawable.provider_qwen
    "ollama" -> R.drawable.provider_ollama
    "open router" -> R.drawable.provider_openrouter
    else -> 0
}
