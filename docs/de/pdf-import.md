# PDF-Import

橘子岛 kann ausgewählte Seiten aus PDF-Dateien extrahieren und als Bilder an vision-fähige Modelle senden.

## Wie es funktioniert

1. Hängen Sie eine PDF-Datei im Chat an
2. Ein Dialog öffnet sich und zeigt alle Seiten als Miniaturansichten
3. Wählen Sie aus, welche Seiten gesendet werden sollen
4. Bestätigen — 橘子岛 extrahiert die Seiten als Bilder und sendet sie an das Modell

Das Modell empfängt die Seiten als Vision-Eingabe, sodass es PDF-Inhalte lesen und analysieren kann.

---

## Seitenauswahl

### Seiten wählen

- Jede Seitenminiatur hat ein **Kontrollkästchen** in der oberen linken Ecke
- Tippen Sie auf das Kontrollkästchen, um eine Seite ein-/auszuschalten
- **Alle auswählen** / **Auswahl aufheben**-Buttons für Sammeloperationen
- Die obere Leiste zeigt die ausgewählte Anzahl (z.B. "3 ausgewählt")
- Maximal **50 Seiten** pro PDF (größere Dateien werden abgeschnitten)

### Vollbildvorschau

Tippen Sie auf eine beliebige Miniaturansicht, um den Vollbildbetrachter zu öffnen:

| Geste | Aktion |
|---------|--------|
| Nach links/rechts wischen | Zwischen Seiten navigieren |
| Tippen | Overlay-Steuerelemente ein-/ausblenden |
| Zusammenziehen | Vergrößern/Verkleinern |
| Kapsel unten links | Seitenauswahl umschalten |

Die Vorschau ermöglicht es Ihnen, Seiten zu inspizieren, bevor Sie entscheiden, welche gesendet werden sollen.

---

## Seiten senden

Nach der Auswahl der Seiten tippen Sie auf den Bestätigungsbutton. 橘子岛:

1. Rendert jede ausgewählte PDF-Seite als hochauflösendes Bild
2. Hängt die Bilder an Ihre Nachricht an
3. Sendet sie an das Modell (erfordert ein vision-fähiges Modell)

---

## Einschränkungen

- **Max. 50 Seiten** pro PDF — größere Dateien werden abgeschnitten
- **Nur Bild**: Text wird nicht extrahiert; das Modell liest Seiten visuell
- **Vision-Modell erforderlich**: Das aktive Chat-Modell muss Bildeingaben unterstützen
- **Dateigröße**: Obwohl es kein hartes PDF-Größenlimit gibt, können sehr große Dateien langsam zu rendern sein

---

## Anwendungsfälle

- **Dokumentanalyse** — laden Sie einen Vertrag, Bericht oder Aufsatz zur Überprüfung durch das Modell hoch
- **Forschung** — teilen Sie bestimmte Seiten aus wissenschaftlichen Arbeiten
- **Übersetzung** — senden Sie fremdsprachige Dokumente zur Übersetzung
- **Zusammenfassung** — erhalten Sie Zusammenfassungen langer Dokumente, Seite für Seite
