# Generierungsparameter

Steuern Sie, wie Modelle Antworten generieren — von der Kontextlänge bis zu Kreativitätseinstellungen.

## Kontextfenster

**Max Kontext-Nachrichten** legt fest, wie viele aktuelle Nachrichten als Kontext an das Modell gesendet werden. Standard: **20**.

- **5–20** — Kürzerer Kontext, schnellere Antworten, weniger Token-Verbrauch
- **20–50** — Längerer Kontext für komplexe, mehrzügige Konversationen
- **50–100** — Maximaler Kontext für sehr lange Diskussionen (kann Token-Limits erreichen)

Dies gilt für alle Modelle. Das tatsächliche Kontextfenster in Tokens hängt von Ihrem Modell und der Nachrichtenlänge ab.

---

## Temperature

Steuert die Zufälligkeit der Modellausgabe. Bereich: **0,0 – 2,0**.

- **0,0 – 0,3** — Deterministischer, konsistenter, faktenorientierter
- **0,5 – 0,8** — Ausgewogene Kreativität (empfohlener Standard)
- **1,0 – 2,0** — Zufälliger, kreativer, unvorhersehbarer

Höhere Temperature bedeutet, dass das Modell eher weniger wahrscheinliche Wörter wählt. Niedrigere Temperature erzeugt fokussiertere, wiederholendere Ausgaben.

!!! tip "Wann anpassen"
    - **Code / Fakten**: Niedrige Temperature verwenden (0,0 – 0,3)
    - **Kreatives Schreiben**: Hohe Temperature verwenden (0,8 – 1,2)
    - **Allgemeiner Chat**: Mittlere Temperature verwenden (0,5 – 0,7)

---

## Top P (Nucleus Sampling)

Steuert die Diversität der Token-Auswahl. Bereich: **0,0 – 1,0**.

Das Modell berücksichtigt nur die kleinste Menge von Tokens, deren kumulative Wahrscheinlichkeit `top_p` überschreitet.

- **0,1** — Sehr fokussiert, nur die wahrscheinlichsten Tokens
- **0,5** — Moderate Diversität
- **0,9 – 1,0** — Volle Diversität (empfohlener Standard)

Normalerweise passen Sie *entweder* Temperature *oder* Top P an — nicht beides.

---

## Standard Max Tokens

Legt ein maximales Token-Limit für Modellantworten fest. Wenn gesetzt, generiert das Modell nicht mehr als diese Anzahl von Tokens in einer einzelnen Antwort. Wenn **nicht gesetzt** (Standard), gilt das eigene Maximum des Modells.

Verfügbare Voreinstellungen:

```
256   512   1024   2048
4096  8192  16384  32768
```

!!! tip "Für Flexibilität nicht setzen"
    Für die meisten Anwendungsfälle lassen Sie dies ungesetzt. Setzen Sie ein Limit nur, wenn Sie konsistente Antwortlängen benötigen (z.B. kurze Zusammenfassungen) oder Kosten deckeln möchten.

---

## Frequency Penalty

Reduziert die Tendenz des Modells, dieselben Wörter zu wiederholen. Bereich: **-2,0 – 2,0**.

- **Positive Werte** (0,1 – 1,0) — Wiederholung unterdrücken
- **Null** (0,0) — Keine Penalty (Standard)
- **Negative Werte** (-1,0 – -0,1) — Wiederholung fördern

---

## Presence Penalty

Ermutigt das Modell, über neue Themen zu sprechen. Bereich: **-2,0 – 2,0**.

- **Positive Werte** (0,1 – 1,0) — Themenvielfalt fördern
- **Null** (0,0) — Keine Penalty (Standard)
- **Negative Werte** — Beim aktuellen Thema bleiben

---

## Thinking / Reasoning

Aktiviert Chain-of-Thought-Reasoning für unterstützte Modelle (z.B. DeepSeek R1, Qwen3, Claude).

Wenn aktiviert, generiert das Modell internes Reasoning, bevor es die endgültige Antwort produziert. Dies verbessert die Genauigkeit bei komplexen Aufgaben, dauert aber länger und verbraucht mehr Tokens.

### Thinking-Level

- **Niedrig** — Minimales Reasoning, schneller
- **Mittel** — Ausgewogen (Standard)
- **Hoch** — Maximales Reasoning für komplexe Probleme

!!! warning "Nicht alle Modelle unterstützen Thinking"
    Thinking-Modus erfordert ein Modell, das Reasoning-Tokens unterstützt. Wenn Ihr Modell dies nicht unterstützt, hat diese Einstellung keine Wirkung.

---

## Kontext-Rollout visualisieren

Wenn aktiviert, zeigt 橘子岛 visuell an, welche Nachrichten im aktuellen Kontextfenster enthalten sind und welche herausgerollt wurden (aufgrund der Kontextfenster-Begrenzung ausgeschlossen). Dies hilft Ihnen zu verstehen:

- Wie viel Ihrer Konversation das Modell "sehen" kann
- Wann ältere Nachrichten aus dem Kontext fallen
- Ob Sie das Kontextfenster vergrößern müssen

Die Visualisierung erscheint als dezente Markierung in der Konversationsansicht.

---

## Wie Parameter funktionieren

Alle Generierungsparameter sind **nullable** — wenn sie nicht explizit gesetzt sind, werden sie nicht an das Modell gesendet, und das Modell verwendet seine eigenen Standards. Jeder Parameter hat eine Zurücksetzungsoption, um den Wert auf "nicht gesetzt" zurückzusetzen.

---

## Konversationsbezogene Überschreibungen

Sie können Generierungsparameter für einzelne Konversationen über den **Erweiterte Einstellungen**-Dialog im Chat-Bildschirm überschreiben (langer Druck auf den Senden-Button oder verwenden Sie das ⋮-Menü).
