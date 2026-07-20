# Modelle

Verwalten Sie, welche KI-Modelle verfügbar sind, und legen Sie Ihr Standardmodell für Konversationen fest.

## Modellliste

Die **Modelle**-Seite zeigt alle Modelle, die 橘子岛 kennt, nach Provider organisiert:

- **Standardmodell** — Das Modell, das für neue Konversationen verwendet wird. Tippen zum Ändern.
- **Verfügbare Modelle** — Erweitern Sie jeden Provider, um seine Modelle zu sehen. Aktivieren Sie die, die Sie verwenden möchten.

### Modelle aktivieren / deaktivieren

Aktivieren oder deaktivieren Sie das Kontrollkästchen neben einem Modell, um seine Verfügbarkeit umzuschalten. Deaktivierte Modelle erscheinen nicht in der Modellauswahl in Konversationen.

### Modelle umbenennen

Tippen Sie auf das Bearbeiten-Symbol (Stift) neben einem Modell, um ihm einen benutzerdefinierten Alias zu geben. Dieser Alias erscheint in der gesamten App anstelle der technischen Modell-ID.

### Modelle synchronisieren

Tippen Sie auf **Modelle synchronisieren**, um die neuesten verfügbaren Modelle von allen konfigurierten API-Providern abzurufen. Dies erfordert eine Internetverbindung und gültige API-Keys.

!!! tip "Lokale Modelle"
    Lokale Modelle erscheinen im **Lokal**-Provider-Bereich. Sie werden separat in **Einstellungen → Provider → Lokal** verwaltet.

---

## Standardmodell

Das **Standardmodell** wird für alle neuen Konversationen verwendet. So ändern Sie es:

1. Tippen Sie auf die Standardmodell-Zeile oben auf der Modelle-Seite
2. Wählen Sie ein Modell aus der Liste (nur aktivierte Modelle werden angezeigt)
3. Die Änderung wird sofort wirksam

Sie können das Modell pro Konversation über die Modellauswahl im Chat-Bildschirm überschreiben.

---

## Modell-Aliase

Modell-Aliase ermöglichen es Ihnen, Modellen mit langen technischen IDs freundliche Namen zu geben. Zum Beispiel könnten Sie `openai/gpt-4o-mini` in einfach "GPT-4o Mini" umbenennen.

Aliase werden überall angezeigt: in der Modellauswahl, in Konversationskopfeilen und auf Einstellungsseiten.

Um einen Alias zu entfernen, leeren Sie das Textfeld und speichern Sie.

---

## Problembehandlung

### Modelle erscheinen nicht

- Tippen Sie auf **Modelle synchronisieren**, um die Liste zu aktualisieren
- Überprüfen Sie, ob Sie einen gültigen API-Key für den Provider in **Einstellungen → Provider** haben
- Überprüfen Sie Ihre Internetverbindung
- Einige Provider sind möglicherweise vorübergehend nicht verfügbar

### Lokale Modelle werden nicht angezeigt

- Importieren Sie eine GGUF-Modelldatei in **Einstellungen → Provider → Lokal**
- Das Modell muss im gültigen GGUF-Format vorliegen
