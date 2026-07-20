# Web Search

Let the model search the internet and fetch web pages in real time. When enabled, the model can look up current information, verify facts, retrieve documentation, or research topics — all autonomously via tool calling.

## Supported Providers

| Provider | Description | Free Tier | Setup |
|----------|-------------|-----------|-------|
| **DuckDuckGo Lite** | Anonymous, no API key needed | Yes (unlimited, best-effort) | No setup — works out of the box |
| **Brave** | Privacy-focused search API | Yes (2,000 queries/month) | [api.search.brave.com](https://api.search.brave.com/) |
| **Serper** | Fast Google Search API | Yes (2,500 queries/month) | [serper.dev](https://serper.dev) |
| **Tavily** | AI-optimized search, built for LLM agents | Yes (1,000 queries/month) | [tavily.com](https://tavily.com) |
| **SearXNG** | Self-hosted metasearch engine | Self-hosted (unlimited) | Your own instance |

## Setup

### DuckDuckGo Lite

DuckDuckGo Lite is the **default** search provider — no API key required, works immediately.

1. In 橘子岛, go to **Settings → Web Search**
2. Select **DuckDuckGo Lite** as the search provider
3. No key or URL needed — start searching right away

!!! note "Best-effort service"
    DuckDuckGo Lite uses HTML scraping of `lite.duckduckgo.com`. DDG may change their layout, rate-limit, or block automated requests. It ships as an explicitly best-effort, no-key option. If you need reliability, configure one of the API-based providers below.

### Brave

1. Get an API key from [Brave Search API](https://api.search.brave.com/)
2. In 橘子岛, go to **Settings → Web Search**
3. Select **Brave** as the search provider
4. Paste your API key

### Serper

1. Get an API key from [serper.dev](https://serper.dev)
2. In 橘子岛, go to **Settings → Web Search**
3. Select **Serper**
4. Paste your API key

### Tavily

1. Get an API key from [tavily.com](https://tavily.com)
2. In 橘子岛, go to **Settings → Web Search**
3. Select **Tavily**
4. Paste your API key

### SearXNG

1. Set up a SearXNG instance (self-hosted) or use a public instance
2. In 橘子岛, go to **Settings → Web Search**
3. Select **SearXNG**
4. Enter your instance's **Base URL** (e.g., `https://searx.be`)
5. API key is optional (only needed if your instance requires authentication)

!!! warning "Public Instances"
    Public SearXNG instances are often rate-limited or unreliable. Self-hosting is recommended for consistent use.

---

## Configuration

### Max Results

Set how many search results to fetch per query: **1–10**. Default is device-dependent. More results give the model more context but cost more tokens.

### Enable/Disable

Toggle **Enable Web Search** on the Web Search settings page. When disabled, the model cannot call the web search tool.

---

## How the Model Uses Search

When you ask a question that needs current or external information, the model automatically calls web search:

1. **Search**: Model calls the search API with a query it formulates
2. **Fetch**: Model can optionally fetch full page content from result URLs
3. **Synthesize**: Model reads the results and integrates them into its response

You'll see each search and fetch as inline tool cards in the conversation.

### Example

```text
You: "What's the latest version of Python?"
Model: [Searches "latest Python version 2026"]
       [Reads result]
       "Python 3.14.0 was released in October 2025..."
```

---

## Web Fetch

Beyond search, the model can fetch and read specific web pages. When the model encounters a URL in search results, it can call `web_fetch` to retrieve the full page content:

- The fetched content is converted to markdown
- The model processes it and extracts relevant information
- Fetch results are shown as tool cards

---

## Privacy Considerations

When using web search:

- Your queries go to the search provider (Brave, Serper, etc.), not to 橘子岛
- 橘子岛 does not log or store your search queries (except in the conversation itself)
- SearXNG self-hosting gives you the most privacy — queries stay on your infrastructure
