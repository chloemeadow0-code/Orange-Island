# Embedding / RAG

Embedding-Modelle konvertieren Text in numerische Vektoren, die Bedeutung erfassen. 橘子岛 verwendet diese Vektoren für die semantische Suche (RAG) über Ihre Konversationshistorie — Nachrichten nach ihrer Bedeutung zu finden, nicht nur nach den enthaltenen Wörtern.

## Wie es funktioniert

1. Jede Nachricht wird an ein Embedding-Modell gesendet
2. Das Modell gibt einen Vektor (eine Liste von Zahlen) zurück, der die Bedeutung der Nachricht repräsentiert
3. Wenn Sie suchen, wird Ihre Anfrage ebenfalls eingebettet
4. 橘子岛 berechnet die **Kosinus-Ähnlichkeit** zwischen dem Anfragevektor und allen Nachrichtenvektoren
5. Nachrichten mit einer Ähnlichkeit über Ihrem Schwellenwert werden als Treffer zurückgegeben

## Unterstützte Provider

| Provider | Basis-URL | Erfordert API-Key | Hinweise |
|----------|----------|------------------|-------|
| **OpenAI** | `https://api.openai.com/v1` | Ja | `text-embedding-3-small`, `text-embedding-3-large` |
| **Mistral** | `https://api.mistral.ai/v1` | Ja | `mistral-embed` |
| **Voyage AI** | `https://api.voyageai.com/v1` | Ja | `voyage-3`, `voyage-3-lite` |
| **SiliconFlow** | `https://api.siliconflow.cn/v1` | Ja | `BAAI/bge-large-zh-v1.5` (Chinesisch-optimiert) |
| **Ollama** | `http://localhost:11434/v1` | Nein | `qwen3-embedding`, `nomic-embed-text`, etc. |
| **Benutzerdefiniert** | Beliebig | Optional | Jeder OpenAI-kompatible Embeddings-Endpunkt |
| **Lokal** | N/A | Nein | GGUF-Embedding-Modelle via llama.cpp |

---

## Ein Embedding-Modell hinzufügen

### Remote (API)

1. Gehen Sie zu **Einstellungen → Konversationssuche**
2. Tippen Sie auf **Remote-Modell hinzufügen**
3. Konfigurieren Sie:

| Feld | Beschreibung |
|-------|-------------|
| **Provider** | Aus der Dropdown-Liste wählen (OpenAI, Mistral, Voyage, SiliconFlow, Ollama, Benutzerdefiniert) |
| **Modellname** | Die genaue Modell-ID (z.B. `text-embedding-3-small`) |
| **Basis-URL** | Für bekannte Provider automatisch ausgefüllt; für Proxies bearbeitbar |
| **API-Key** | Leer lassen, um automatisch von Ihrem Chat-Provider-Key aufzulösen, oder einen dedizierten Key eingeben |
| **Batch-Größe** | Nachrichten pro API-Anfrage (1–100) |

4. Tippen Sie auf **Hinzufügen** — ein Verbindungstest wird vor dem Speichern ausgeführt

!!! tip
    Das API-Key-Feld ist optional, wenn Sie denselben Provider bereits für Chat konfiguriert haben. Lassen Sie es leer und 橘子岛 löst Ihren Chat-API-Key automatisch auf.

### Lokal (GGUF)

1. Gehen Sie zu **Einstellungen → Konversationssuche**
2. Tippen Sie auf **Lokales Modell hinzufügen**
3. Importieren Sie eine `.gguf`-Embedding-Modelldatei (z.B. `bge-small-en-v1.5-q4_k.gguf`)
4. Geben Sie ihm einen Namen
5. Tippen Sie auf **Hinzufügen**

Embedding-Modelle sind typischerweise viel kleiner als Chat-Modelle — höchstens einige hundert MB.

### Ollama

1. Installieren Sie Ollama auf einem Rechner
2. Laden Sie ein Embedding-Modell: `ollama pull qwen3-embedding:8b`
3. Fügen Sie in 橘子岛 ein Remote-Modell hinzu:
    - Provider: **Ollama**
    - Basis-URL: `http://<host>:11434/v1`
    - Modellname: `qwen3-embedding:8b` (mit dem `:tag`)
    - API-Key: leer lassen
4. Tippen Sie auf **Hinzufügen**

!!! note
    Ollama-Suffix-Tags wie `:8b`, `:latest` sind Teil des Modellnamens. Verwenden Sie den genauen Namen aus `ollama list`.

---

## Caching

Nach dem Hinzufügen eines Modells müssen Sie Ihre Nachrichten cachen (Embeddings generieren):

1. Tippen Sie auf **Cachen** beim Embedding-Modell
2. 橘子岛 verarbeitet alle nicht zwischengespeicherten Nachrichten in Batches
3. Ein kreisförmiger Fortschrittsindikator zeigt den aktuellen Fortschritt
4. Abschluss: "Alle N Nachrichten gecacht"

### Auto-Cache

Aktivieren Sie **Auto-Cache**, um neue Nachrichten automatisch einzubetten, sobald sie eintreffen. Dies hält Ihren Suchindex stets aktuell.

### Neu-Cachen

Tippen Sie auf **Neu cachen**, um alle vorhandenen Embeddings zu löschen und von Grund auf neu aufzubauen. Verwenden, wenn:

- Sie zu einem anderen Embedding-Modell wechseln
- Die Embedding-Qualität verschlechtert erscheint
- Der Cache inkonsistent ist

!!! warning
    Neu-Caching kann nicht rückgängig gemacht werden und kann bei großen Nachrichtenhistorien lange dauern.

---

## Batch-Größe

Die **Batch-Größe**-Einstellung (1–100) steuert, wie viele Nachrichten pro API-Anfrage beim Caching gesendet werden:

- **Höher**: Schnelleres Caching, aber größere API-Payloads
- **Niedriger**: Kleinere Anfragen, langsamer aber zuverlässiger bei langsamen Verbindungen

Beginnen Sie mit dem Standard und passen Sie an, wenn Sie Timeouts erleben (verringern) oder schnelleres Caching wünschen (erhöhen).

---

## Ihre Einrichtung testen

Wenn Sie ein Remote-Modell hinzufügen, führt 橘子岛 einen automatischen Verbindungstest durch. Wenn er fehlschlägt:

1. Überprüfen Sie den Modellnamen — Tags für Ollama einschließen (`:8b`, `:latest`)
2. Stellen Sie sicher, dass die Basis-URL von Ihrem Gerät aus erreichbar ist
3. Bestätigen Sie, dass der API-Key gültig ist (falls erforderlich)
4. Versuchen Sie einen bekannten Modellnamen für diesen Provider

Häufige Fehler:
- **"Falscher Modellname"** — genaue Schreibweise prüfen, einschließlich Tags
- **"Falsche Basis-URL"** — sicherstellen, dass der Endpunkt `/v1/embeddings` unterstützt
- **"Fehlender API-Key"** — einige Provider erfordern Authentifizierung
- **"Netzwerkfehler"** — Konnektivität prüfen

---

## Provider-Empfehlungen

| Anwendungsfall | Empfohlener Provider |
|----------|---------------------|
| **Beste Qualität (Englisch)** | Voyage AI `voyage-3` |
| **Beste Qualität (Chinesisch)** | SiliconFlow `BAAI/bge-large-zh-v1.5` |
| **Kostenlos / Selbst gehostet** | Ollama `qwen3-embedding` oder `nomic-embed-text` |
| **Vollständig offline** | Lokal GGUF `bge-small-en-v1.5` |
| **Bereits OpenAI-Nutzer** | OpenAI `text-embedding-3-small` (günstig, schnell) |
