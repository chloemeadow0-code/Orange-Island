# System-Prompts

System-Prompts definieren die Persona, das Verhalten und die Grundregeln des Modells. 橘子岛 gibt Ihnen feinkörnige Kontrolle darüber, wie Anweisungen zusammengestellt und an das Modell gesendet werden.

## Drei-Abschnitts-Editor

Jede System-Prompt-Vorlage hat drei unabhängig bearbeitbare Abschnitte:

```text
┌─────────────────────────────────┐
│ System-Prompt                   │ ← Kernanweisungen (Persona, Regeln, Ton)
├─────────────────────────────────┤
│ Benutzer-Präfix                 │ ← Vor jeder Benutzernachricht vorangestellt
├─────────────────────────────────┤
│ Benutzer-Suffix                 │ ← Nach jeder Benutzernachricht angehängt
└─────────────────────────────────┘
```

### System-Prompt

Der Hauptanweisungsblock. Hier definieren Sie:

- **Persona**: "Du bist ein erfahrener Python-Entwickler mit Fokus auf saubere Architektur."
- **Regeln**: "Antworte immer auf Chinesisch. Verwende Aufzählungspunkte für Listen."
- **Einschränkungen**: "Entschuldige dich nie. Sei prägnant. Bevorzuge Code gegenüber Erklärungen."

### Benutzer-Präfix & Suffix

Diese umschließen jede von Ihnen gesendete Nachricht:

- **Benutzer-Präfix** — vor Ihrem Nachrichtentext hinzugefügt. Nützlich für Erinnerungen oder Kontext-Tags.
- **Benutzer-Suffix** — nach Ihrem Nachrichtentext hinzugefügt. Nützlich für abschließende Anweisungen.

**Beispiel**: Wenn Ihr Präfix `[Kontext: Arbeite an 橘子岛-Dokumentation]` und Suffix `\n\nBitte antworte in Markdown.` ist, erhält das Modell:

```text
[Kontext: Arbeite an 橘子岛-Dokumentation]
Wie konfiguriere ich die Websuche?
Bitte antworte in Markdown.
```

---

## Einen Prompt erstellen

1. Gehen Sie zu **Einstellungen → System-Prompts**
2. Tippen Sie auf **Neuen Prompt hinzufügen**
3. Geben Sie einen **Titel** ein (z.B. "Übersetzer", "Code-Reviewer", "Chinesischer Assistent")
4. Füllen Sie die drei Abschnitte aus:
    - Tippen Sie auf **Text hinzufügen**, um statischen Inhalt zu schreiben
    - Tippen Sie auf **Variable hinzufügen**, um dynamische Werte einzufügen
5. Tippen Sie auf **Speichern**

### Elemente neu anordnen

Innerhalb jedes Abschnitts können Sie mehrere Textblöcke und Variablen haben. Langer Druck auf ein Element zum:

- **Nach oben** / **Nach unten** — innerhalb des Abschnitts neu anordnen
- **Entfernen** — das Element löschen

---

## Variablensubstitution

Variablen werden durch dynamische Werte ersetzt, wenn die Nachricht gesendet wird:

| Variable | Erweitert zu | Beispiel | Auflösung |
|----------|-----------|---------|---------------|
| `{time}` | Aktuelle Uhrzeit (HH:mm:ss) | `14:30:00` | Prompt-Kompilierung |
| `{date}` | Aktuelles Datum (YYYY-MM-DD) | `2026-05-10` | Prompt-Kompilierung |
| `{sent_time}` | Sendezeit der Nachricht (HH:mm) | `10:05` | Pro Nachricht |
| `{sent_date}` | Sendedatum der Nachricht (YYYY-MM-DD) | `2026-05-11` | Pro Nachricht |
| `{active_memory}` | Inhalt des aktiven Speichers | `[Ihr gespeicherter Speicherinhalt]` | Prompt-Kompilierung |
| `{model_id}` | Aktuell ausgewählte Modell-ID | `gemini-1.5-flash` | Prompt-Kompilierung |

**Pro-Nachricht-Variablen** (`{sent_time}`, `{sent_date}`) werden jedes Mal aufgelöst, wenn Sie eine Nachricht senden, sodass sie die genaue Sendezeit widerspiegeln. **Prompt-Ebene-Variablen** (`{time}`, `{date}`, `{active_memory}`, `{model_id}`) werden bei der Prompt-Kompilierung aufgelöst.

!!! tip
    Verwenden Sie `{sent_date}` für datumsabhängige Prompts wie "Heute ist {sent_date}. Wenn Sie über aktuelle Ereignisse sprechen, beachten Sie, dass Ihr Wissen veraltet sein kann." Verwenden Sie `{active_memory}`, um den persistenten Speicher des Modells in Systemanweisungen einzufügen.

### Eine Variable hinzufügen

1. Tippen Sie in einem beliebigen Abschnitt des Editors auf **Variable hinzufügen**
2. Wählen Sie die Variable aus der Auswahl
3. Sie erscheint als Pill/Chip im Abschnitt — ziehen Sie sie zum Neupositionieren

---

## Prompts verwalten

### Als Standard festlegen

Tippen Sie auf den Radio-Button neben einem Prompt, um ihn zum **globalen Standard** zu machen. Alle Konversationen verwenden diesen Prompt, sofern nicht überschrieben.

### Konversationsbezogene Überschreibung

Jede Konversation kann einen anderen System-Prompt verwenden:

1. Öffnen Sie eine Konversation
2. Tippen Sie auf das Overflow-Menü (:material-dots-vertical:) in der oberen Leiste
3. Wählen Sie **Konversations-Prompt**
4. Wählen Sie einen Prompt aus der Liste

Die konversationsbezogene Einstellung überschreibt den globalen Standard nur für diese Konversation.

### Bearbeiten oder Löschen

- Tippen Sie auf einen Prompt, um ihn zu **bearbeiten**
- Langer Druck und wählen Sie **Löschen**, um ihn zu entfernen

!!! warning
    Das Löschen eines System-Prompts ist dauerhaft. Konversationen, die ihn verwendet haben, fallen auf den globalen Standard zurück.

---

## Kein System-Prompt

Wenn kein System-Prompt ausgewählt ist, erhält das Modell keine speziellen Anweisungen — es verhält sich gemäß seinem Basistraining. Dies ist manchmal wünschenswert für Tests oder für Modelle, die ohne Systemanweisungen besser funktionieren.

Um keinen Prompt zu verwenden, wählen Sie **Keiner** aus der Prompt-Liste.

---

## Automatische Titelgenerierung

橘子岛 kann nach der ersten Antwort automatisch Konversationstitel generieren:

1. Gehen Sie zu **Einstellungen → Titelgenerierung**
2. Aktivieren Sie **Titel automatisch generieren**
3. Wählen Sie ein **Titel-Modell**:
    - **Aktuelles Modell verwenden** — verwendet das in der Konversation aktive Modell
    - **Titel-Modell auswählen** — wählen Sie ein bestimmtes schnelles/günstiges Modell für die Titelgenerierung

Wenn aktiviert, erscheint eine kurze "Generiere Titel..."-Snackbar nach der ersten Modellantwort und die Konversation wird automatisch von "Unbenannt" in einen beschreibenden Titel umbenannt.

---

## Prompt-Beispiele

### Übersetzer

```yaml
System-Prompt: |
  Du bist ein professioneller Übersetzer. Übersetze Benutzereingaben ins Englische.
  Behalte Formatierung, Code-Blöcke und technische Begriffe bei. Füge keine Erklärungen hinzu.
```

### Code-Reviewer

```yaml
System-Prompt: |
  Du bist ein erfahrener Code-Reviewer. Wenn dir Code gezeigt wird:
  1. Identifiziere Bugs und Randfälle
  2. Schlage Leistungsverbesserungen vor
  3. Prüfe auf Sicherheitsprobleme
  Sei spezifisch. Verweise wenn möglich auf Zeilennummern.
```

### Chinesischer Assistent

```yaml
System-Prompt: |
  你是一个乐于助人的中文助手。用简洁、清晰的中文回答问题。
Benutzer-Suffix: |
  \n\n请用中文回答。
```
