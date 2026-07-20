# API-Provider

橘子岛 verbindet sich direkt mit KI-Providern — kein Mittelsmann, kein Abonnement, keine Telemetrie. Sie bringen Ihre eigenen API-Keys mit und alles läuft von Ihrem Gerät aus.

## Integrierte Provider

| Provider | Basis-URL | Modelle | Hinweise |
|----------|----------|--------|-------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Gemini-Serie | Kostenloses Kontingent via Google AI Studio verfügbar |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4, GPT-4o, o-Serie | Reasoning-Modelle unterstützt |
| **Anthropic** | `https://api.anthropic.com/v1` | Claude-Serie | Extended Thinking unterstützt |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3, DeepSeek-R1 | Reasoning-Modelle unterstützt |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Qwen-Serie | Via Alibaba DashScope |
| **Ollama** | `http://localhost:11434/v1` | Jedes geladene Modell | Selbst gehostet, kein API-Key nötig |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Multi-Provider | Zugriff auf viele Modelle über eine API |
| **Lokal** | N/A | GGUF-Modelle | Auf dem Gerät via llama.cpp, vollständig offline |

## Provider wechseln

Tippen Sie auf die Provider-Auswahl in den Einstellungen, um zwischen Providern zu wechseln. Jeder Provider verwaltet seine eigenen:

- API-Keys
- Basis-URL (bearbeitbar für Proxies/Self-Hosted)
- Modellliste

---

## API-Keys

### Mehrere Keys pro Provider

Jeder Provider unterstützt mehrere benannte API-Keys. Dies ermöglicht:

- **Rotation** — zwischen Keys für verschiedene Nutzungskontingente wechseln
- **Organisation** — berufliche und private Nutzung trennen
- **Fallback** — einen Backup-Key bereithalten

### Keys verwalten

1. Gehen Sie zu **Einstellungen → Provider**
2. Wählen Sie einen Provider
3. Unter **API-Keys** tippen Sie auf **Neuen Key hinzufügen**
4. Geben Sie einen **Namen** (z.B. "Arbeit", "Privat", "Team Shared") und den **Key-Wert** ein
5. Tippen Sie auf **Hinzufügen**

Tippen Sie auf den Radio-Button, um den aktiven Key festzulegen. Langer Druck auf einen Key zum **Bearbeiten** oder **Löschen**.

### Key-Sicherheit

!!! warning
    API-Keys werden lokal in einer verschlüsselten Room-Datenbank gespeichert. Sie werden niemals an 橘子岛-Server gesendet (es gibt keine). Sie werden jedoch im Klartext exportiert, wenn Sie sie in eine `.橘子岛`-Exportdatei einschließen.

---

## Benutzerdefinierte Provider

Fügen Sie jeden OpenAI-kompatiblen API-Endpunkt hinzu:

1. Gehen Sie zu **Einstellungen → Provider**
2. Tippen Sie auf **+ Benutzerdefinierten Provider hinzufügen** am Ende der Provider-Liste
3. Geben Sie ein:
    - **Provider-Name** — ein beliebiger Anzeigename
    - **Basis-URL** — der API-Endpunkt
4. Tippen Sie auf **Hinzufügen**

橘子岛 ruft die Modellliste von `{base_url}/v1/models` ab. Einmal hinzugefügt, funktionieren benutzerdefinierte Provider genauso wie integrierte: API-Keys hinzufügen, Modelle synchronisieren und chatten.

### Anwendungsfälle

- **Self-Hosted** — Verbindung zu vLLM, LocalAI, text-generation-webui oder anderen OpenAI-kompatiblen Servern
- **Proxies** — Route über einen Unternehmens-Proxy oder API-Gateway
- **Alternative Endpunkte** — Azure OpenAI, Cloudflare AI Gateway oder andere kompatible Dienste nutzen

### Umbenennen oder Löschen

Langer Druck auf einen benutzerdefinierten Provider zum **Umbenennen** oder **Löschen**. Löschen entfernt den Provider und alle seine Keys.

!!! warning
    Integrierte Provider können nicht umbenannt oder gelöscht werden.

---

## Basis-URL-Überschreibung

Jeder Provider (einschließlich integrierter) hat eine bearbeitbare **Basis-URL**. Dies ist nützlich für:

- **Proxies**: Route über `https://my-proxy.example.com/v1`
- **Self-Hosted**: Auf Ihre eigene Instanz verweisen
- **Region-Routing**: Regionsspezifische Endpunkte verwenden

---

## Modelle synchronisieren

Nach dem Hinzufügen von API-Keys synchronisieren Sie die Modellliste:

1. Gehen Sie zu **Einstellungen → Modelle**
2. Tippen Sie auf **Von allen Providern synchronisieren**
3. 橘子岛 ruft verfügbare Modelle von jedem konfigurierten Provider ab

Eine Snackbar zeigt den Synchronisierungsfortschritt und die Ergebnisse an. Sie können dann einzelne Modelle aktivieren/deaktivieren und ein Standardmodell festlegen.

---

## Provider-spezifische Hinweise

### Google Gemini

- API-Keys von [Google AI Studio](https://aistudio.google.com/apikey)
- Kostenloses Kontingent mit Ratenlimits verfügbar
- Unterstützt Code-Ausführung und Search Grounding (integrierte Tools)

### OpenAI

- API-Keys von [Platform](https://platform.openai.com/api-keys)
- Reasoning-Modelle (o1, o3) erfordern spezifischen API-Zugriff
- Streaming, Tools und Vision werden alle unterstützt

### Anthropic

- API-Keys von [Console](https://console.anthropic.com/)
- Extended Thinking mit konfigurierbaren Token-Budgets
- Tool-Nutzung mit parallelen Aufrufen unterstützt

### Ollama

- Kein API-Key erforderlich (lokales Netzwerk)
- Basis-URL typischerweise `http://<host>:11434/v1`
- Modellliste wird von Ollamas API abgerufen
- Siehe [FAQ](faq.md) für Ollama-spezifische Problembehandlung

### OpenRouter

- Einzelner API-Key für 200+ Modelle
- Pay-per-Token-Preise variieren je nach Modell
- Gut zum Ausprobieren verschiedener Modelle ohne individuelle Provider-Konten

### Lokal (llama.cpp)

- Kein Netzwerk erforderlich
- GGUF-Modelldateien auf dem Gerät gespeichert
- Siehe [Lokale Modelle](local-model.md) für die Einrichtung
