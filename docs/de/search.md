# Konversationssuche

橘子岛 kann Ihre gesamte Konversationshistorie durchsuchen — entweder durch Schlüsselwortabgleich oder semantische (bedeutungsbasierte) Suche mit Embedding-Modellen.

## Suchmethoden

### Schlüsselwortsuche

Schneller, exakter Textabgleich. Die Suche sucht nach wörtlichen Vorkommen Ihrer Anfrage im Nachrichteninhalt.

**Am besten für:**
- Finden einer bestimmten Phrase oder eines Begriffs
- Schnelle Nachschlage, wenn Sie sich an den genauen Wortlaut erinnern
- Keine Einrichtung — funktioniert sofort

**Einschränkungen:**
- Übersieht Synonyme und verwandte Konzepte
- Kein Bedeutungsverständnis

### Semantische Suche (RAG)

Verwendet Embedding-Modelle, um Nachrichten nach **Bedeutung** zu finden, nicht nach exakten Wörtern. Eine Anfrage wie "Wie richte ich die Datenbank ein" kann Nachrichten über "Room-Konfiguration" finden, auch wenn das Wort "Datenbank" nie vorkommt.

**Am besten für:**
- Finden von Konversationen nach Thema oder Themenbereich
- Breite Anfragen, bei denen Sie sich nicht an den genauen Wortlaut erinnern
- Entdecken verwandter Diskussionen über verschiedene Konversationen hinweg

**Voraussetzungen:**
- Ein Embedding-Modell muss konfiguriert sein (siehe [Embedding / RAG](embedding.md))
- Nachrichten müssen gecacht sein (Embeddings generiert)

---

## Einrichtung

### 1. Ein Embedding-Modell hinzufügen

Siehe [Embedding / RAG](embedding.md) für die detaillierte Einrichtung. Sie können verwenden:
- **Remote-Modelle** (OpenAI, Mistral, Voyage, Ollama, etc.)
- **Lokale Modelle** (GGUF-Dateien, vollständig offline)

### 2. Suchmethoden wählen

In **Einstellungen → Konversationssuche**:

| Einstellung | Beschreibung |
|---------|-------------|
| **Modell-Suchmethode** | Wie das Modell sucht, wenn es das `search_conversations`-Tool aufruft |
| **Manuelle Suchmethode** | Wie die Suchleiste in der Konversationsleiste funktioniert |

Setzen Sie jede auf **Schlüsselwort** oder **Semantisch (RAG)**.

### 3. Suchumfang konfigurieren

| Einstellung | Bereich | Beschreibung |
|---------|-------|-------------|
| **Kontext-Nachrichten pro Suchtreffer** | 4–32 | Wie viele umgebende Nachrichten mit jedem Treffer eingeschlossen werden (Schritte: 4, 8, 12, 16, 20, 24, 28, 32) |
| **Max Suchergebnisse** | 5–30 | Maximale Anzahl zurückgegebener Treffer (Schritte: 5, 10, 15, 20, 25, 30) |
| **Ähnlichkeitsschwelle** | 0,0–1,0 | Nur RAG: minimaler Ähnlichkeitswert für einen Treffer. Höher = strenger. Standard: 0,5 |

### 4. Nachrichten cachen

Wenn Sie RAG verwenden, tippen Sie auf **Cachen**, um Embeddings für alle vorhandenen Nachrichten zu generieren. Aktivieren Sie **Auto-Cache**, um den Index automatisch aktuell zu halten.

---

## Suche verwenden

### Manuelle Suche (Suchleiste)

1. Öffnen Sie die **Konversationsleiste** (Hamburger-Menü :material-menu: oder nach rechts wischen)
2. Tippen Sie auf die Suchleiste oben
3. Geben Sie Ihre Anfrage ein
4. Ergebnisse erscheinen darunter — tippen Sie auf ein Ergebnis, um diese Konversation bei der passenden Nachricht zu öffnen

### Modell-initiierte Suche

Wenn **Zugriff auf Vergangene Konversationen** aktiviert ist (Einstellungen → Speicher), kann das Modell Ihre Historie autonom durchsuchen:

```text
Sie: "Was haben wir letzte Woche über das API-Design entschieden?"
Modell: [Sucht "API-Design-Entscheidung"]
       "Letzten Dienstag haben wir entschieden..."
```

Die Suche erscheint als Tool-Karte in der Konversation.

---

## Ähnlichkeitsschwelle

Der **Ähnlichkeitsschwelle**-Schieberegler (0,0 bis 1,0) steuert, wie eng eine Nachricht übereinstimmen muss, um in RAG-Ergebnisse aufgenommen zu werden:

- **Niedrig (0,3–0,5)**: Mehr Ergebnisse, kann lose verwandte Inhalte enthalten
- **Mittel (0,5–0,7)**: Ausgewogen — guter Standard
- **Hoch (0,7–0,9)**: Weniger Ergebnisse, nur sehr enge Übereinstimmungen

Beginnen Sie mit dem Standard und passen Sie basierend auf Ihren Ergebnissen an. Wenn Sie zu viele irrelevante Treffer erhalten, erhöhen Sie die Schwelle. Wenn Sie relevante Konversationen verpassen, senken Sie sie.

---

## Anzeige der Suchergebnisse

In der Konversationsleiste zeigen Suchergebnisse:

- **Konversationstitel** (oder "Unbenannt")
- **Passende Nachricht** — die Benutzer- oder Modellnachricht, die übereinstimmte
- **Rollenlabel** — Benutzer oder Modell
- **Kontext-Nachrichten** — umgebende Nachrichten als Kontext

Tippen Sie auf ein Ergebnis, um die Konversation zur passenden Nachricht gescrollt zu öffnen.
