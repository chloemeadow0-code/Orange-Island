# Websuche

Lassen Sie das Modell das Internet durchsuchen und Webseiten in Echtzeit abrufen. Wenn aktiviert, kann das Modell aktuelle Informationen nachschlagen, Fakten überprüfen, Dokumentation abrufen oder Themen recherchieren — alles autonom via Tool-Aufruf.

## Unterstützte Provider

| Provider | Beschreibung | Kostenloses Kontingent | Einrichtung |
|----------|-------------|-----------|-------|
| **DuckDuckGo Lite** | Anonym, kein API-Key erforderlich | Ja (unbegrenzt, Best-Effort) | Keine Einrichtung — funktioniert sofort |
| **Brave** | Datenschutzorientierte Such-API | Ja (2.000 Anfragen/Monat) | [api.search.brave.com](https://api.search.brave.com/) |
| **Serper** | Schnelle Google Search API | Ja (2.500 Anfragen/Monat) | [serper.dev](https://serper.dev) |
| **Tavily** | KI-optimierte Suche, für LLM-Agenten entwickelt | Ja (1.000 Anfragen/Monat) | [tavily.com](https://tavily.com) |
| **SearXNG** | Selbst gehostete Metasuchmaschine | Selbst gehostet (unbegrenzt) | Ihre eigene Instanz |

## Einrichtung

### DuckDuckGo Lite

DuckDuckGo Lite ist der **Standard**-Suchprovider — kein API-Key erforderlich, funktioniert sofort.

1. Gehen Sie in 橘子岛 zu **Einstellungen → Websuche**
2. Wählen Sie **DuckDuckGo Lite** als Suchprovider
3. Kein Key oder URL nötig — sofort mit der Suche beginnen

!!! note "Best-Effort-Dienst"
    DuckDuckGo Lite verwendet HTML-Scraping von `lite.duckduckgo.com`. DDG kann das Layout ändern, Ratenbegrenzungen anwenden oder automatisierte Anfragen blockieren. Es wird ausdrücklich als Best-Effort-Option ohne Key angeboten. Wenn Sie Zuverlässigkeit benötigen, konfigurieren Sie einen der API-basierten Provider unten.

### Brave

1. Holen Sie sich einen API-Key von [Brave Search API](https://api.search.brave.com/)
2. Gehen Sie in 橘子岛 zu **Einstellungen → Websuche**
3. Wählen Sie **Brave** als Suchprovider
4. Fügen Sie Ihren API-Key ein

### Serper

1. Holen Sie sich einen API-Key von [serper.dev](https://serper.dev)
2. Gehen Sie in 橘子岛 zu **Einstellungen → Websuche**
3. Wählen Sie **Serper**
4. Fügen Sie Ihren API-Key ein

### Tavily

1. Holen Sie sich einen API-Key von [tavily.com](https://tavily.com)
2. Gehen Sie in 橘子岛 zu **Einstellungen → Websuche**
3. Wählen Sie **Tavily**
4. Fügen Sie Ihren API-Key ein

### SearXNG

1. Richten Sie eine SearXNG-Instanz ein (selbst gehostet) oder verwenden Sie eine öffentliche Instanz
2. Gehen Sie in 橘子岛 zu **Einstellungen → Websuche**
3. Wählen Sie **SearXNG**
4. Geben Sie die **Basis-URL** Ihrer Instanz ein (z.B. `https://searx.be`)
5. API-Key ist optional (nur erforderlich, wenn Ihre Instanz Authentifizierung verlangt)

!!! warning "Öffentliche Instanzen"
    Öffentliche SearXNG-Instanzen sind oft ratenbegrenzt oder unzuverlässig. Selbst-Hosting wird für konsistente Nutzung empfohlen.

---

## Konfiguration

### Maximale Ergebnisse

Legen Sie fest, wie viele Suchergebnisse pro Anfrage abgerufen werden: **1–10**. Der Standard ist geräteabhängig. Mehr Ergebnisse geben dem Modell mehr Kontext, kosten aber mehr Tokens.

### Aktivieren/Deaktivieren

Schalten Sie **Websuche aktivieren** auf der Websuche-Einstellungsseite um. Wenn deaktiviert, kann das Modell das Websuche-Tool nicht aufrufen.

---

## Wie das Modell die Suche verwendet

Wenn Sie eine Frage stellen, die aktuelle oder externe Informationen benötigt, ruft das Modell automatisch die Websuche auf:

1. **Suchen**: Modell ruft die Such-API mit einer von ihm formulierten Anfrage auf
2. **Abrufen**: Modell kann optional den vollständigen Seiteninhalt von Ergebnis-URLs abrufen
3. **Synthetisieren**: Modell liest die Ergebnisse und integriert sie in seine Antwort

Sie sehen jede Suche und jeden Abruf als Inline-Tool-Karten in der Konversation.

### Beispiel

```text
Sie: "Was ist die neueste Version von Python?"
Modell: [Sucht "neueste Python-Version 2026"]
       [Liest Ergebnis]
       "Python 3.14.0 wurde im Oktober 2025 veröffentlicht..."
```

---

## Web-Abruf

Über die Suche hinaus kann das Modell bestimmte Webseiten abrufen und lesen. Wenn das Modell in den Suchergebnissen auf eine URL stößt, kann es `web_fetch` aufrufen, um den vollständigen Seiteninhalt abzurufen:

- Der abgerufene Inhalt wird in Markdown konvertiert
- Das Modell verarbeitet ihn und extrahiert relevante Informationen
- Abrufergebnisse werden als Tool-Karten angezeigt

---

## Datenschutzüberlegungen

Bei der Nutzung der Websuche:

- Ihre Anfragen gehen an den Suchprovider (Brave, Serper, etc.), nicht an 橘子岛
- 橘子岛 protokolliert oder speichert Ihre Suchanfragen nicht (außer in der Konversation selbst)
- SearXNG-Selbst-Hosting bietet den größten Datenschutz — Anfragen bleiben auf Ihrer Infrastruktur
