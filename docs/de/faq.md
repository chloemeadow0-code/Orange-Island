# Häufig gestellte Fragen

## API & Provider

### Wie bekomme ich einen API-Key?

- **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — kostenloses Kontingent verfügbar
- **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
- **Anthropic**: [Console API Keys](https://console.anthropic.com/)
- **DeepSeek**: [Platform](https://platform.deepseek.com/)
- **OpenRouter**: [Keys-Seite](https://openrouter.ai/keys)
- **Brave Search**: [Brave Search API](https://api.search.brave.com/)

### Kann ich mehrere API-Keys für denselben Provider verwenden?

Ja. Jeder Provider unterstützt mehrere benannte Keys. Tippen Sie auf den Radio-Button, um den aktiven Key auszuwählen. Nützlich zum Wechseln zwischen Arbeits-/Privat-Keys oder um einen Backup bereitzuhalten. Siehe [API-Provider](provider.md#api-keys).

### Wie füge ich einen benutzerdefinierten Provider hinzu?

Gehen Sie zu Einstellungen → Provider → **+ Benutzerdefinierten Provider hinzufügen**. Geben Sie einen Namen und eine Basis-URL ein. Jeder OpenAI-kompatible Endpunkt funktioniert. Siehe [Benutzerdefinierte Provider](provider.md#custom-providers).

---

## Lokale Modelle

### Welche GGUF-Modelle funktionieren?

橘子岛 unterstützt das GGUF-Format sowohl für Chat als auch für Embedding. Chat-Modelle sollten in den Gerätespeicher passen (1–8B Parameter je nach RAM). Embedding-Modelle sind viel kleiner (100–500 MB). Siehe [Lokale Modelle](local-model.md).

### Wie führe ich Modelle offline aus?

Importieren Sie ein GGUF-Chat-Modell via Einstellungen → Provider → Lokal → **GGUF-Modell importieren**. Für vollständig offline semantische Suche importieren Sie auch ein GGUF-Embedding-Modell. Keine Netzwerkverbindung erforderlich.

### Warum ist mein lokales Modell so langsam?

Lokale Inferenz läuft auf der CPU Ihres Geräts. Sie ist von Natur aus langsamer als Cloud-APIs. Tipps: Verwenden Sie kleinere Modelle (1–3B Parameter), niedrigere Quantisierung (Q4_K_M), kürzere Kontextfenster und schließen Sie Hintergrund-Apps.

---

## Embeddings & Suche

### Warum schlägt mein Embedding-Modell-Test fehl?

Häufige Ursachen:

- **Falscher Modellname** — überprüfen Sie die genaue Schreibweise, einschließlich Ollama-Tags (z.B. `qwen3-embedding:8b` nicht `qwen3-embedding`)
- **Falsche Basis-URL** — stellen Sie sicher, dass der Endpunkt `/v1/embeddings` unterstützt
- **Fehlender API-Key** — einige Provider erfordern Authentifizierung auch für Embeddings
- **Netzwerk** — überprüfen Sie die Konnektivität zum Endpunkt

### Was ist der Unterschied zwischen Schlüsselwort- und RAG-Suche?

Schlüsselwortsuche findet exakten Text. RAG (semantische Suche) findet nach Bedeutung — "Datenbank einrichten" kann "Room-Konfiguration" finden, auch ohne gemeinsame Wörter. RAG erfordert ein Embedding-Modell und zwischengespeicherte Nachrichten. Siehe [Konversationssuche](search.md).

### Wie verwende ich Ollama für Embeddings?

1. Installieren Sie Ollama auf einem Rechner
2. Laden Sie ein Embedding-Modell: `ollama pull qwen3-embedding:8b`
3. Fügen Sie in 橘子岛 ein Remote-Embedding-Modell mit der **Ollama**-Voreinstellung hinzu
4. Verwenden Sie `http://<host>:11434/v1` als Basis-URL
5. Geben Sie den genauen Modellnamen inklusive Tag ein (z.B. `qwen3-embedding:8b`)
6. Lassen Sie den API-Key leer

---

## Speicher

### Was ist der Unterschied zwischen Aktivem Speicher und Gespeicherten Erinnerungen?

**Aktiver Speicher** ist ein einzelner persistenter Kontext, der bei jedem API-Aufruf mitgesendet wird — das Modell sieht ihn immer. **Gespeicherte Erinnerungen** sind eine Sammlung benannter Dateien, die das Modell bei Bedarf durchsucht und abruft. Verwenden Sie Aktiven Speicher für persistente Fakten; verwenden Sie Gespeicherte Erinnerungen für Referenzmaterial. Siehe [Speicher & Cache](memory.md).

### Kann das Modell meine Erinnerungen ändern?

Ja, wenn Sie **Zugriff auf Gespeicherte Erinnerungen** und/oder **Zugriff auf Aktiven Speicher** in Einstellungen → Speicher aktivieren. Das Modell kann Erinnerungen via Tool-Aufrufe erstellen, lesen, bearbeiten und löschen. Alle Berechtigungen sind standardmäßig deaktiviert.

---

## Shell & Tools

### Wie richte ich Remote-Shell-Zugriff ein?

Stellen Sie den [Conch](https://github.com/orangeisland/conch)-Server auf Ihrem Zielrechner bereit und fügen Sie dann das Gerät in Einstellungen → Shell mit URL und API-Key hinzu. Siehe [Remote Shell](shell.md).

### Kann ich ohne API-Key im Web suchen?

Ja. **DuckDuckGo Lite** ist der Standard-Websuchprovider und benötigt keinen API-Key. Es funktioniert sofort — aktivieren Sie einfach Websuche unter Einstellungen → Websuche. Für höhere Zuverlässigkeit konfigurieren Sie einen der API-basierten Provider (Brave, Serper, Tavily, SearXNG). Siehe [Websuche](web-search.md).

### Ist die Shell-Verbindung verschlüsselt?

Ja. Conch verwendet ECDH-Schlüsselaustausch + AES-256-GCM-Verschlüsselung + HMAC-SHA256-Signierung. Der gesamte Datenverkehr zwischen 橘子岛 und dem Conch-Server ist Ende-zu-Ende verschlüsselt.

---

## Daten

### Wie sichere ich meine Daten?

Gehen Sie zu Einstellungen → Datenkontrolle → **Daten exportieren**, um eine manuelle Sicherung als `.橘子岛`-Datei zu erstellen. Für automatischen Schutz aktivieren Sie **Automatische Sicherung** unter Einstellungen → Datenkontrolle → Automatische Sicherung — sie sichert Ihre Daten regelmäßig im Hintergrund. Siehe [Datenportabilität](import-export.md).

### Kann ich von ChatGPT oder Claude importieren?

Ja. Exportieren Sie Ihre Daten von ChatGPT oder Claude (sie liefern `.zip`-Dateien) und importieren Sie sie dann in Einstellungen → Datenkontrolle → **Drittanbieter**. Sowohl Merge- als auch Replace-Strategien werden unterstützt. Siehe [Datenportabilität](import-export.md#third-party-import).

### Sind meine API-Keys in Exporten enthalten?

Sie können enthalten sein, aber es ist optional. Der Export-Bildschirm lässt Sie die Einbeziehung von API-Keys umschalten. Eine Warnung wird angezeigt, wenn Sie es aktivieren. Keys werden im Klartext in der `.橘子岛`-Datei gespeichert, also schließen Sie sie nur für vollständige Gerätemigrationen an vertrauenswürdige Ziele ein.

---

## Allgemein

### Wo werden meine Daten gespeichert?

Alles wird lokal auf Ihrem Android-Gerät in einer Room-Datenbank gespeichert. 橘子岛 hat keine Server, keine Cloud-Synchronisation, keine Telemetrie. Nachrichten werden direkt von Ihrem Gerät an den von Ihnen konfigurierten KI-Provider gesendet.

### Unterstützt 橘子岛 mehrere Sprachen?

Ja. Die App-Benutzeroberfläche unterstützt **English**, **中文 (Chinesisch)** und **繁體中文 (Traditionelles Chinesisch)**. Einstellungen → Sprache. Ein Neustart ist nach dem Wechsel erforderlich.

### Wie melde ich einen Fehler oder wünsche eine Funktion?

Eröffnen Sie ein Issue auf [GitHub](https://github.com/orangeisland/app/issues). Für Beiträge siehe den [Contributing](https://github.com/orangeisland/app#contributing)-Abschnitt der README.
