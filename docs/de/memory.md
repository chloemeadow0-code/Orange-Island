# Speicher & Cache

橘子岛 verfügt über ein persistentes Speichersystem, das es dem Modell ermöglicht, sich Informationen über Konversationen hinweg zu merken. Kombiniert mit automatischem Embedding-basiertem Caching bietet es eine Wissensbasis, die mit Ihrer Nutzung wächst.

## Speichertypen

### Aktiver Speicher

Ein einzelner, ständig aktiver Speicherkontext, der bei **jedem API-Aufruf** an das Modell gesendet wird. Denken Sie daran wie an einen Haftnotizzettel, den das Modell immer sieht.

**Aktiven Speicher verwenden für:**
- Ihren Namen, Präferenzen und Hintergrund
- Projektkontext, den das Modell immer kennen sollte
- Dauerhafte Anweisungen, die für alle Konversationen gelten
- Fakten, die Sie nicht ständig wiederholen möchten

**Beispiel für Aktiven Speicherinhalt:**
```text
Benutzer: Newo Ether
Präferenzen: Bevorzugt Chinesisch für lockere Chats, Englisch für technische Themen.
Projekt: Entwicklung von 橘子岛 — einem BYOK Android LLM-Client.
Codierungsstil: Kotlin, Jetpack Compose, MVVM-Architektur.
```

#### Aktiven Speicher bearbeiten

1. Gehen Sie zu **Einstellungen → Speicher**
2. Scrollen Sie zu **Aktiver Speicher**
3. Tippen Sie auf **Aktiven Speicher bearbeiten**
4. Geben Sie Ihren Inhalt ein
5. Tippen Sie auf **Speichern**

Das Modell kann den aktiven Speicher auch via Tool-Aufrufe aktualisieren, wenn **Zugriff auf Aktiven Speicher** aktiviert ist.

---

### Gespeicherte Erinnerungen

Eine Sammlung benannter Speicherdateien, die das Modell durchsuchen, lesen, erstellen, bearbeiten und löschen kann. Im Gegensatz zum Aktiven Speicher (immer gesendet) werden gespeicherte Erinnerungen bei Bedarf abgerufen.

**Gespeicherte Erinnerungen verwenden für:**
- Referenzmaterial (API-Dokumentation, Konfigurationsdetails, Befehle)
- Projektspezifische Notizen
- Erkenntnisse und Einsichten aus vergangenen Konversationen
- Alles, woran sich das Modell bei Relevanz erinnern soll

#### Erinnerungen manuell erstellen

1. Gehen Sie zu **Einstellungen → Speicher**
2. Tippen Sie auf **Erinnerung hinzufügen**
3. Geben Sie ein:
    - **Titel** — beschreibender Name
    - **Beschreibung** — kurze Zusammenfassung (für Suchabgleich verwendet)
    - **Inhalt** — der vollständige Erinnerungsinhalt
4. Tippen Sie auf **Erstellen**

#### Vom Modell erstellte Erinnerungen

Wenn **Zugriff auf Gespeicherte Erinnerungen** aktiviert ist, kann das Modell Speicherdateien via Tool-Aufrufe erstellen, lesen, aktualisieren und löschen. Dies ermöglicht dem Modell:

- Sich Fakten zu merken, die Sie ihm mitteilen
- Nützliche Code-Snippets oder Konfigurationen zu speichern
- Eine Wissensbasis im Laufe der Zeit aufzubauen
- Veraltete Informationen zu bereinigen

---

## Speicherberechtigungen

Steuern Sie, worauf das Modell zugreifen kann:

| Einstellung | Ort | Wann aktivieren |
|---------|----------|----------------|
| **Zugriff auf Gespeicherte Erinnerungen** | Einstellungen → Speicher | Wenn das Modell Speicherdateien lesen/schreiben soll |
| **Zugriff auf Aktiven Speicher** | Einstellungen → Speicher | Wenn das Modell den persistenten Kontext aktualisieren soll |
| **Zugriff auf Vergangene Konversationen** | Einstellungen → Konversationssuche | Wenn das Modell den Chatverlauf durchsuchen soll |

Alle drei sind standardmäßig **deaktiviert**. Aktivieren Sie nur, was Sie benötigen.

---

## Auto-Cache

Auto-Caching generiert automatisch Embeddings für neue Nachrichten, sobald sie eintreffen. Dies hält Ihren Konversationssuchindex ohne manuelles Eingreifen aktuell.

### Auto-Cache aktivieren

1. Gehen Sie zu **Einstellungen → Konversationssuche**
2. Wählen Sie ein Embedding-Modell (falls noch nicht geschehen — siehe [Embedding / RAG](embedding.md))
3. Unter **Caching** aktivieren Sie **Neue Nachrichten automatisch cachen**

Wenn aktiviert, wird jede neue Nachricht (Benutzer und Modell) automatisch eingebettet und für die semantische Suche indiziert.

### Manuelles Caching

Wenn Auto-Cache deaktiviert ist, können Sie Nachrichten manuell cachen:

1. Gehen Sie zu **Einstellungen → Konversationssuche**
2. Tippen Sie auf **Cachen** — berechnet Embeddings für alle nicht zwischengespeicherten Nachrichten
3. Der Fortschritt wird als kreisförmiger Indikator angezeigt

Tippen Sie auf **Neu cachen**, um den gesamten Index von Grund auf neu aufzubauen. Dies löscht alle zwischengespeicherten Embeddings und verarbeitet jede Nachricht neu. Verwenden, wenn:
- Sie das Embedding-Modell gewechselt haben
- Der Cache beschädigt oder veraltet erscheint
- Die Suchergebnisse unerwartet schlecht sind

!!! warning
    Neu-Caching ist irreversibel und kann je nach Nachrichtenanzahl und Embedding-Modell-Geschwindigkeit eine Weile dauern.

### Cache-Status

Die Embedding-Modell-Einstellungen zeigen, wie viele Nachrichten gecacht vs. ungecacht sind:
- **"Alle N Nachrichten gecacht"** — aktuell
- **"X von Y Nachrichten nicht gecacht"** — Rückstand zu verarbeiten

---

## Speicher-Tool-Aufrufe im Chat

Wenn das Modell Speicher-Tools verwendet, sehen Sie Inline-Karten:

| Tool | Kartentext |
|------|-----------|
| Durchsuchen | "N gespeicherte Erinnerungen durchsucht" |
| Lesen | "[Erinnerungsname] gelesen" |
| Speichern | "[Erinnerungsname] gespeichert" |
| Bearbeiten | "[Erinnerungsname] aktualisiert" |
| Löschen | "[Erinnerungsname] entfernt" |
| Aktiv aktualisieren | "Aktiven Speicher aktualisiert" |

Tippen Sie auf eine Karte, um den vollständigen Inhalt zu sehen, der gelesen oder geschrieben wurde.

---

## Bewährte Praktiken

- **Aktiven Speicher knapp halten** — er ist in jedem API-Aufruf enthalten, daher verschwendet ausführlicher Inhalt Tokens
- **Beschreibende Titel für Gespeicherte Erinnerungen verwenden** — Titel helfen dem Modell, die richtige Erinnerung zu finden
- **Auto-Cache aktivieren**, wenn Sie die Konversationssuche regelmäßig nutzen
- **Nach dem Wechsel des Embedding-Modells neu cachen** — verschiedene Modelle erzeugen inkompatible Embeddings
