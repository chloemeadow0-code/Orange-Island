# 橘子岛 Benutzerhandbuch

Willkommen zum 橘子岛 Benutzerhandbuch. 橘子岛 ist ein BYOK (Bring Your Own Key) LLM-Client für Android mit Multi-Provider-Zugriff, nicht-linearen verzweigten Konversationen, agentischem Tool-Aufruf, Bildgenerierung und Fernsteuerung von Geräten.

## Schnelllinks

### Erste Schritte

- **[Erste Schritte](getting-started.md)** — installieren, konfigurieren und die erste Nachricht senden
- **[FAQ](faq.md)** — Antworten auf häufige Fragen

### Kernfunktionen

- **[Konversationen](conversations.md)** — nicht-lineare Verzweigung, Nachrichtenoperationen, Streaming, Markdown-Darstellung
- **[API-Provider](provider.md)** — Verbindung zu OpenAI, Anthropic, Google, DeepSeek, Ollama und benutzerdefinierten Endpunkten
- **[Modelle](models.md)** — Modelle aktivieren/deaktivieren, Aliase, provider-übergreifende Modellsynchronisation
- **[System-Prompts](system-prompts.md)** — Drei-Abschnitts-Editor, Variablensubstitution, konversationsbezogener Wechsel
- **[Generierung](generation.md)** — Temperature, Top P, Max Tokens, Thinking, Frequency/Presence Penalties
- **[Titelgenerierung](title-generation.md)** — Konversationstitel automatisch generieren
- **[Bildtranskription](transcription.md)** — Bild-zu-Text-Pipeline für vision-blinde Provider
- **[Bildgenerierung](image-generation.md)** — Text-zu-Bild-Generierung als Chat-Tool
- **[Erscheinungsbild](appearance.md)** — Theme-Modus, Farbschema, Dynamic Color, Schema-Stil, Blur-Effekte

### Agentische Tools

- **[Übersicht](tools.md)** — wie mehrstufige Tool-Aufrufe funktionieren
- **[Websuche](web-search.md)** — DuckDuckGo Lite, Brave, Serper, Tavily, SearXNG Integration
- **[Remote Shell (Conch)](shell.md)** — verschlüsselte Remote-Befehlsausführung, Dateioperationen, MCP-Integration
- **[Sandbox](sandbox.md)** — lokale Alpine Linux-Umgebung für isolierte Befehlsausführung

### Wissensverwaltung

- **[Konversationssuche](search.md)** — Schlüsselwort- und semantische (RAG) Suche über den Chatverlauf
- **[Embedding / RAG](embedding.md)** — Embedding-Modelle für semantische Suche konfigurieren
- **[Speicher & Cache](memory.md)** — Aktiver Speicher, gespeicherte Erinnerungen, Auto-Caching

### Mehr

- **[Lokale Modelle](local-model.md)** — GGUF-Modelle auf dem Gerät via llama.cpp ausführen
- **[PDF-Import](pdf-import.md)** — PDF-Seiten extrahieren und an Vision-Modelle senden
- **[Datenportabilität](import-export.md)** — .橘子岛-Dateien exportieren/importieren, automatische Sicherung, Import von Claude und ChatGPT
- **[Sprache](language.md)** — zwischen Englisch, Chinesisch, Traditionellem Chinesisch oder Systemstandard wechseln
- **[Über](about.md)** — Versionsinfo, Updates, Dokumentationsschalter, Links, Bewertung

---

## Über 橘子岛

橘子岛 ist ein BYOK Android-Client für KI-Power-User:

- **Keine Mittelsmänner**: Direkte API-Verbindungen, keine Telemetrie, kein Tracking
- **Lokale Speicherung**: Alles lebt lokal in einer Room-Datenbank
- **Nicht-lineare Konversationen**: Jede vergangene Nachricht bearbeiten und alternative Zweige erkunden
- **Standardmäßig agentisch**: Mehrstufige Tool-Aufrufe mit Websuche, Bildgenerierung, Code-Ausführung, Shell, Dateioperationen und Speicher
- **Fernsteuerung**: Server über das verschlüsselte Conch-Protokoll verwalten
- **Open Source**: MIT-lizenziert, [Quellcode auf GitHub](https://github.com/chloemeadow0-code/Orange-Island)
