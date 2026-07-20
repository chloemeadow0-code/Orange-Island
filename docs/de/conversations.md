# Konversationen

橘子岛s Konversationssystem basiert auf **nicht-linearer Verzweigung** — im Gegensatz zu den meisten Chat-Apps können Sie jede vergangene Nachricht bearbeiten und alternative Antwortpfade erkunden, ohne die ursprüngliche Konversation zu verlieren.

## Konversationen erstellen

Tippen Sie auf **Neuer Chat** in der Konversationsleiste oder beginnen Sie einfach, im Chat-Bildschirm zu tippen. Eine neue Konversation wird automatisch mit Ihrer ersten Nachricht erstellt.

Konversationen werden nach der ersten Antwort automatisch betitelt (wenn [Titelgenerierung](system-prompts.md#auto-title-generation) aktiviert ist), oder Sie können sie manuell umbenennen.

## Konversationen verwalten

### Konversationen wechseln

Öffnen Sie die **Konversationsleiste** (Hamburger-Menü :material-menu: oder nach rechts wischen) und tippen Sie auf eine Konversation, um sie zu öffnen.

### Umbenennen

1. Langer Druck auf eine Konversation in der Leiste
2. Tippen Sie auf **Umbenennen**
3. Geben Sie einen neuen Titel ein und speichern Sie

### Löschen

1. Langer Druck auf eine Konversation in der Leiste
2. Tippen Sie auf **Löschen**
3. Bestätigen Sie die Löschung — diese Aktion kann nicht rückgängig gemacht werden

---

## Nicht-lineare Verzweigung

Dies ist 橘子岛s Signatur-Feature. Jede Nachricht kann ein Verzweigungspunkt sein.

### Eine vergangene Nachricht bearbeiten

1. Langer Druck auf eine beliebige Nachrichtenblase (Benutzer oder Modell)
2. Tippen Sie auf **Bearbeiten**
3. Ändern Sie den Nachrichteninhalt
4. Senden — 橘子岛 erstellt einen **neuen Zweig** von diesem Punkt aus

Der ursprüngliche Zweig bleibt erhalten. Sie können jederzeit zwischen Zweigen wechseln.

### Wie Zweige funktionieren

Jede Nachricht lebt in einer **Baumstruktur**:

```text
Nachricht 1 (Benutzer)
├── Nachricht 2 (Modell) ← ursprüngliche Antwort
└── Nachricht 3 (Modell) ← Zweig erstellt nach Bearbeitung von Nachricht 1
    ├── Nachricht 4 (Benutzer)
    └── ...
```

Wenn Sie eine Nachricht bearbeiten und neu generieren, wird die neue Antwort ein Geschwister der ursprünglichen — beide existieren unter derselben Elternnachricht.

### Zweige wechseln

Wenn eine Nachricht mehrere Kinder (Zweige) hat, zeigt die Benutzeroberfläche Navigationssteuerelemente zum Wechseln zwischen ihnen. Sie können alternative Pfade erkunden, ohne den Kontext zu verlieren.

### Warum verzweigen?

- **Alternativen erkunden** — dieselbe Frage mit anderem Wortlaut stellen
- **A/B-Test-Prompts** — Antworten von verschiedenen System-Prompts oder Modellen vergleichen
- **Fehler beheben** — einen Tippfehler in Ihrer Frage korrigieren, ohne den ursprünglichen Thread zu verlieren
- **Iterieren** — einen Prompt durch mehrere Versionen verfeinern und dabei alle Versuche behalten

---

## Nachrichtenoperationen

Langer Druck auf eine Nachricht, um auf diese Aktionen zuzugreifen:

| Aktion | Beschreibung |
|--------|-------------|
| **Kopieren** | Nachrichtentext in die Zwischenablage kopieren |
| **Bearbeiten** | Nachricht bearbeiten und einen Zweig erstellen |
| **Info** | Metadaten anzeigen: Zeitstempel, verwendetes Modell, Token-Anzahl |
| **Löschen** | Diese Nachricht und alle Folgeantworten löschen |

!!! warning "Eine Nachricht löschen"
    Das Löschen einer Nachricht entfernt auch alle Antworten, die ihr folgen. Dies kann nicht rückgängig gemacht werden.

---

## Die untere Leiste

Der Chat-Eingabebereich bietet schnellen Zugriff auf wichtige Steuerelemente:

### Modellauswahl

Tippen Sie auf den Modellnamen auf der linken Seite der unteren Leiste, um die **Modellauswahl** zu öffnen. Sie können Modelle jederzeit wechseln — sogar mitten in der Konversation. Verschiedene Nachrichten in derselben Konversation können verschiedene Modelle verwenden.

### Anhänge

Tippen Sie auf **+** (:material-plus:), um Dateien anzuhängen:

- **Fotos** — Bilder aus Ihrer Galerie
- **Videos** — Videodateien (mit Frame-Extraktion-Unterstützung)
- **Dateien** — jeder Dateityp, einschließlich PDFs

Unterstützte Bildformate werden direkt an vision-fähige Modelle gesendet. PDF-Dateien öffnen einen Seitenauswahl-Dialog.

### Senden

Geben Sie Ihre Nachricht ein und tippen Sie auf **Senden** (:material-send:). Das Modell streamt seine Antwort Token für Token.

---

## Streaming & Anzeige

### Echtzeit-Streaming

Antworten erscheinen Wort für Wort, während das Modell sie generiert. 橘子岛 scrollt automatisch, um den neuesten Inhalt sichtbar zu halten. Tippen Sie auf den **Zum Ende scrollen**-Button (erscheint, wenn Sie nach oben scrollen), um zur Live-Antwort zurückzuspringen.

### Markdown-Darstellung

Modellantworten werden mit vollständiger Markdown-Unterstützung gerendert:

- **Überschriften**, **fett**, *kursiv*, `Inline-Code`
- **Code-Blöcke** mit Syntaxhervorhebung (verwenden Sie ````` ``` `````)
- **Tabellen**, Blockzitate, Listen
- **LaTeX-Mathematik** — inline `$E=mc^2$` und Block `$$\int_a^b f(x)dx$$`

### Thinking-Anzeige

Für Modelle, die Reasoning unterstützen (OpenAI o-Serie, Anthropic Extended Thinking, Gemini Thinking, DeepSeek-R1), wird der Denkprozess des Modells in einem **einklappbaren Panel** vor der endgültigen Antwort angezeigt:

- Das Panel zeigt "Denkt nach..." während der Reasoning-Phase
- Nach Abschluss zeigt es die Denkdauer an (z.B. "12s nachgedacht")
- Tippen zum Erweitern/Einklappen des Denkinhalts
- Während des Denkens durchgeführte Tool-Aufrufe werden gezählt (z.B. "8s nachgedacht, 2 Tools aufgerufen")

---

## Konversationsbezogene Einstellungen

Jede Konversation kann globale Standards überschreiben:

- **Modell** — ein anderes Modell für diese Konversation auswählen
- **System-Prompt** — eine andere Systemanweisung verwenden
- **Generierungsparameter** — Temperature, Max Tokens, Thinking-Level

Diese Überschreibungen werden über das Overflow-Menü der Konversation in der oberen Leiste festgelegt.

---

## Kontextfenster

橘子岛 verfolgt die Token-Nutzung in Echtzeit. Wenn eine Konversation das Kontextfenster des Modells überschreitet, werden ältere Nachrichten visuell **abgedunkelt**, um anzuzeigen, dass sie außerhalb des aktiven Kontexts liegen. Das Modell "sieht" abgedunkelte Nachrichten nicht mehr, aber sie bleiben in Ihrer Benutzeroberfläche sichtbar.

Passen Sie die Kontextfenstergröße in **Einstellungen → Generierung → Kontextfenster** an.
