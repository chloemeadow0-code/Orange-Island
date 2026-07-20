# Titelgenerierung

Konversationstitel automatisch basierend auf dem ersten Austausch generieren.

## Was es macht

Wenn Sie eine neue Konversation starten, kann 橘子岛 automatisch einen kurzen, aussagekräftigen Titel basierend auf Ihrer ersten Nachricht und der Antwort des Modells generieren. Dies ersetzt den generischen Titel "Neuer Chat".

## Einrichtung

1. Gehen Sie zu **Einstellungen → Titelgenerierung**
2. Schalten Sie **Titel automatisch generieren** um
3. Wählen Sie optional ein **Modell** für die Titelgenerierung (verwendet standardmäßig das aktuelle Konversationsmodell)

!!! tip "Modellwahl"
    Die Titelgenerierung verbraucht sehr wenige Tokens. Sie können ein günstiges, schnelles Modell (wie GPT-4o Mini oder ein lokales Modell) verwenden, ohne die Qualität Ihrer Konversation zu beeinträchtigen.

## Wie es funktioniert

1. Sie senden Ihre erste Nachricht in einer neuen Konversation
2. Das Modell antwortet (wie gewohnt)
3. Nach Abschluss der Antwort sendet 橘子岛 eine separate, kleine Anfrage zur Titelgenerierung
4. Der generierte Titel wird gespeichert und in der Konversationsliste angezeigt

Die Titelgenerierung läuft nur einmal pro Konversation, beim ersten Austausch.

## Titelgenerierungsmodell

Sie können ein anderes Modell speziell für die Titelgenerierung verwenden:

- **Standard** (keine Auswahl) — Verwendet dasselbe Modell wie die Konversation
- **Bestimmtes Modell** — Verwendet immer dieses Modell für alle Titelgenerierungen, unabhängig davon, welches Modell für die Konversation verwendet wird

Die Verwendung eines dedizierten schnellen Modells für Titel kann Latenz und Kosten reduzieren.
