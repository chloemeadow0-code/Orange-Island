# Network Proxy

Route all of 橘子岛's network traffic through an HTTP or SOCKS proxy. This is useful on restricted networks, for routing requests through a specific gateway, or when a provider is only reachable via a proxy.

The proxy applies to **all** outbound traffic: chat providers, model fetching, web search, embeddings, web page fetching, and crash report submission.

## Setup

Open **Settings → Network → Proxy** and turn on **Enable proxy**, then configure:

| Field | Description |
|-------|-------------|
| **Type** | `HTTP`, `HTTPS`, or `SOCKS5`. HTTP/HTTPS tunnel HTTPS traffic through the proxy via `CONNECT`; SOCKS5 forwards at the socket level. |
| **Host** | Proxy server hostname or IP (e.g. `127.0.0.1`). |
| **Port** | Proxy server port (e.g. `7890`). |
| **Username / Password** | Optional. Only needed if your proxy requires authentication. |

Changes take effect immediately — there is no need to restart the app.

## Bypass list

Hosts and address ranges in the **Bypass list** connect **directly**, ignoring the proxy. Put one entry per line. The default list keeps loopback and private (LAN) addresses direct:

```
localhost
127.0.0.1
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
::1
```

Each line may be:

- an exact host — `localhost`, `192.168.1.10`
- an IPv4 CIDR range — `10.0.0.0/8`
- a wildcard suffix — `*.example.com`

This is why a local Ollama server (e.g. `http://192.168.1.50:11434`) keeps working over your LAN while everything else goes through the proxy.

## Notes

- **HTTPS** type uses the same proxy protocol as HTTP (an HTTP `CONNECT` proxy); pick it if your proxy is labelled "HTTPS".
- The proxy password is included in **encrypted data exports only when "Include API keys" is enabled**.
- If requests fail with timeouts after enabling the proxy, double-check the host/port and that the proxy type matches your server.
