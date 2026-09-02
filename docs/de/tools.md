# Agentische Tools

橘子岛s Modelle können autonom Tools verwenden — sie entscheiden, was sie suchen, ausführen, lesen oder sich merken, ohne dass Sie jede Aktion manuell auslösen müssen. Tools arbeiten in **mehrstufigen Schleifen**: Das Modell kann ein Tool aufrufen, das Ergebnis lesen und dann entscheiden, ein weiteres Tool aufzurufen oder zu antworten.

## Wie Tool-Aufrufe funktionieren

1. Sie senden eine Nachricht
2. Das Modell entscheidet, dass es externe Informationen oder Aktionen benötigt
3. Es emittiert einen **Tool-Aufruf** — eine strukturierte Anfrage mit einem Tool-Namen und Argumenten
4. 橘子岛 führt das Tool auf dem Gerät oder auf einem entfernten Server aus
5. Das Ergebnis wird an das Modell zurückgegeben
6. Das Modell kann ein weiteres Tool aufrufen oder eine endgültige Antwort produzieren

Diese Schleife kann sich innerhalb einer einzelnen Nachrichtenrunde mehrmals wiederholen.

## Verfügbare Tools

### Websuche

Das Internet durchsuchen und Webseiten abrufen. Das Modell kann aktuelle Informationen nachschlagen, Fakten überprüfen oder Dokumentation abrufen.

- **Provider**: DuckDuckGo Lite (Standard, ohne Key), Brave, Serper, Tavily, SearXNG
- **Konfiguration**: Einstellungen → Websuche
- **Anleitung**: [Websuche](web-search.md)

### Bildgenerierung

Generieren Sie Bilder aus Text-Prompts mit einem dedizierten Text-zu-Bild-Modell. Bilder werden inline in der Konversation angezeigt und können im Vollbildmodus betrachtet werden.

- **Provider**: BYOK — verwendet Ihren eigenen API-Key und Basis-URL, unabhängig vom Chat-Modell
- **Konfiguration**: Einstellungen → Bildgenerierung
- **Anleitung**: [Bildgenerierung](image-generation.md)

### Code-Ausführung

Code in einer isolierten Umgebung ausführen:

- **Gemini Code Execution** — integrierte Code-Ausführung für Gemini-Modelle (keine Einrichtung)
- **Sandbox** — lokale Alpine Linux-Umgebung via PRoot, mit Paketverwaltung und SAF-Dateizugriff

### Remote Shell

Befehle auf entfernten Rechnern über das [Conch](https://github.com/newo-ether/conch)-Protokoll ausführen. Das Modell kann Serverstatus prüfen, Dateien verwalten oder Skripte ausführen.

- **Protokoll**: Ende-zu-Ende-verschlüsselt (ECDH + AES-256-GCM)
- **Konfiguration**: Einstellungen → Shell
- **Anleitung**: [Remote Shell](shell.md)

### Dateioperationen

Dateien auf entfernten Geräten über das Conch-Protokoll lesen, schreiben, bearbeiten, glob-suchen und grep-suchen. Das Modell kann entfernte Dateisysteme direkt manipulieren.

!!! note
    Dateioperationen erfordern ein konfiguriertes Conch-Shell-Gerät. Siehe [Remote Shell](shell.md) für die Einrichtung.

### Speicher

Persistente Wissensspeicherung, die Konversationen überspannt:

- **Aktiver Speicher** — immer in jedem API-Aufruf enthalten. Verwenden für Fakten, Präferenzen oder Kontext, an den sich das Modell immer erinnern soll.
- **Gespeicherte Erinnerungen** — eine Sammlung benannter Speicherdateien, die das Modell via Tool-Aufrufe durchsuchen, lesen, schreiben und bearbeiten kann.

Siehe [Speicher & Cache](memory.md) für Details.

### Konversationssuche

Das Modell kann Ihre vergangene Konversationshistorie mit Schlüsselwort- oder semantischen (RAG) Methoden durchsuchen. Dies ermöglicht es ihm, auf frühere Diskussionen zu verweisen, ohne dass Sie sie manuell finden und teilen müssen.

Siehe [Konversationssuche](search.md) für die Einrichtung.

---

## Tool-Benutzeroberfläche im Chat

Wenn ein Tool aufgerufen wird, sehen Sie es inline in der Konversation:

<div class="grid cards" markdown>

- **:material-progress-wrench: Tool-Aufruf-Banner**

    ---

    Zeigt den Tool-Namen und kurzen Status (z.B. :material-magnify: "Sucht 'neueste KI-Nachrichten' im Web").

- **:material-check-circle: Tool-Ergebnis**

    ---

    Nach der Ausführung zeigt das formatierte Ergebnis oder die Zusammenfassung (z.B. "5 Ergebnisse für 'neueste KI-Nachrichten' gefunden").

</div>

### Erweiterbare Details

Tippen Sie auf einen Tool-Aufruf, um ihn zu erweitern und zu sehen:

- **Argumente** — die genauen Parameter, die an das Tool gesendet wurden
- **Ergebnis** — die Rohausgabe der Tool-Ausführung
- **Status** — Erfolg, Fehler oder Teilergebnisse

### Fehlgeschlagene Aufrufe

Wenn ein Tool-Aufruf fehlschlägt, wird das Modell über den Fehler benachrichtigt und kann es erneut versuchen oder anpassen. Sie sehen ein rotes Banner mit der Fehlermeldung.

---

## Tool-Berechtigungen

Sie steuern, auf welche Tools das Modell zugreifen kann:

| Einstellung | Ort | Standard |
|---------|----------|---------|
| Websuche | Einstellungen → Websuche | Aus |
| Shell | Einstellungen → Shell | Aus |
| Speicher (Gespeichert) | Einstellungen → Speicher → Zugriff auf Gespeicherte Erinnerungen | Aus |
| Speicher (Aktiv) | Einstellungen → Speicher → Zugriff auf Aktiven Speicher | Aus |
| Vergangene Konversationen | Einstellungen → Speicher → Zugriff auf Vergangene Konversationen | Aus |
| Konversationssuche | Einstellungen → Konversationssuche | An* |

*Die Fähigkeit des Modells, Konversationen zu durchsuchen, hängt davon ab, ob ein Embedding-Modell konfiguriert ist. Ohne eines ist nur die Schlüsselwortsuche verfügbar.

---

## Mehrstufige Tool-Schleifen

Das Modell kann mehrere Tool-Aufrufe verketten. Zum Beispiel:

1. Benutzer: "Was ist die neueste Linux-Kernel-Version und läuft sie auf meinem Server?"
2. Modell ruft `web_search("neueste Linux-Kernel-Version")` auf
3. Modell ruft `shell_execute("uname -r", device="mein-server")` auf
4. Modell vergleicht Ergebnisse und antwortet

Jeder Tool-Aufruf und sein Ergebnis erscheinen als separate Inline-Elemente in der Konversation vor der endgültigen Textantwort.
