# Datenportabilität

橘子岛 speichert alle Ihre Daten auf dem Gerät und bietet vollständige Import-/Export-Funktionen. Sie besitzen Ihre Daten — bewegen Sie sie hinein, bewegen Sie sie heraus, sichern Sie sie.

## Export

Exportieren Sie Ihre Daten in eine einzelne `.橘子岛`-Datei — ein portables Archiv, das alles enthält, was 橘子岛 speichert.

### Was exportiert wird

Sie wählen, was enthalten sein soll:

| Kategorie | Inhalt |
|----------|----------|
| **Konversationen & Nachrichten** | Gesamter Chatverlauf, Nachrichtenbäume, Zweige |
| **Erinnerungen** | Aktiver Speicher und alle gespeicherten Erinnerungsdateien |
| **System-Prompts** | Alle benutzerdefinierten System-Prompt-Vorlagen |
| **Einstellungen** | App-Konfiguration und Präferenzen |
| **API-Keys** | Alle konfigurierten API-Keys |

!!! danger "API-Key-Warnung"
    API-Keys werden im **Klartext** exportiert. Jeder mit der `.橘子岛`-Datei kann Ihre Keys lesen. Aktivieren Sie den API-Key-Export nur, wenn Sie dem Ziel vertrauen und die Datei sicher behandeln.

### So exportieren Sie

1. Gehen Sie zu **Einstellungen → Datenkontrolle**
2. Tippen Sie auf **Daten exportieren**
3. Wählen Sie, welche Kategorien enthalten sein sollen
4. Tippen Sie auf **Exportieren**
5. Wählen Sie, wo die `.橘子岛`-Datei gespeichert werden soll

---

## Import

Stellen Sie Daten aus einem früheren `.橘子岛`-Export wieder her.

### Importstrategien

Beim Import wählen Sie, wie 橘子岛 mit bereits auf Ihrem Gerät vorhandenen Daten umgeht:

| Strategie | Verhalten |
|----------|----------|
| **Merge** | Neue Elemente hinzufügen, vorhandene behalten. Wenn ein Element mit derselben ID existiert, überschreibt die Importversion es. |
| **Replace** | Alle vorhandenen Daten in den ausgewählten Kategorien löschen, dann importieren. Ein Neuanfang. |
| **Skip** | Nur Elemente importieren, die keinen Konflikt haben. Vorhandene Elemente bleiben unberührt. |

!!! tip
    Verwenden Sie **Merge** für die meisten Fälle — es fügt sicher neue Daten hinzu, während erhalten bleibt, was bereits auf Ihrem Gerät ist.

### So importieren Sie

1. Gehen Sie zu **Einstellungen → Datenkontrolle**
2. Tippen Sie auf **Daten importieren**
3. Wählen Sie eine `.橘子岛`-Datei
4. Überprüfen Sie die Importvorschau — sehen Sie, was in der Datei ist (Exportdatum, Version, Inhaltsanzahlen)
5. Wählen Sie eine Importstrategie
6. Tippen Sie auf **Importieren**

!!! danger "API-Key-Warnung"
    Wenn die Exportdatei API-Keys enthält, warnt 橘子岛 Sie vor dem Import. Keys werden im Klartext importiert. Fahren Sie nur fort, wenn Sie der Quelle der Datei vertrauen.

---

## Drittanbieter-Import

Importieren Sie Konversationen von anderen KI-Chat-Plattformen.

Sowohl Claude als auch ChatGPT exportieren Ihre Daten als **`.zip`-Archiv**. 橘子岛 importiert dieses `.zip` direkt — es muss nicht zuerst entpackt werden, und 橘子岛 akzeptiert **keine** losen `.json`-Dateien.

### Import von Claude

**1. Von Claude exportieren.** Gehen Sie zu [Claude](https://claude.ai/) → **Settings → Data Controls → Export data**. Claude bereitet das Archiv schnell vor — normalerweise in **unter einer Stunde** — und sendet Ihnen einen Download-Link per E-Mail.

!!! warning "Rechtzeitig herunterladen"
    Claudes Download-Link **läuft schnell ab**. Laden Sie die `.zip` herunter, sobald die E-Mail eintrifft — wenn Sie zu lange warten, wird der Link ungültig und Sie müssen einen neuen Export anfordern.

**2. In 橘子岛 importieren.**

1. Gehen Sie zu **Einstellungen → Datenkontrolle → Drittanbieter → Von Claude importieren**
2. Wählen Sie die exportierte `.zip`-Datei
3. Überprüfen Sie die Vorschau — sehen Sie die Anzahl der Konversationen und Nachrichten
4. Wählen Sie die Strategie **Merge** oder **Replace**
5. Tippen Sie auf **Importieren**

!!! note
    橘子岛 liest die Konversationsdaten direkt aus Claudes `.zip`-Export. Anhänge werden erkannt und in der Vorschau angezeigt, aber nur der Nachrichtentext wird importiert — die Anhangsdateien selbst nicht.

### Import von ChatGPT

**1. Von ChatGPT exportieren.** Gehen Sie zu [ChatGPT](https://chatgpt.com/) → **Settings → Data Controls → Export data**. ChatGPT verarbeitet die Anfrage und sendet Ihnen einen Download-Link per E-Mail, wenn sie fertig ist.

!!! info "Geduld haben"
    Der ChatGPT-Export dauert typischerweise **1–2 Tage**. Dies ist normal — warten Sie auf die E-Mail, anstatt erneut anzufordern.

**2. In 橘子岛 importieren.**

1. Gehen Sie zu **Einstellungen → Datenkontrolle → Drittanbieter → Von ChatGPT importieren**
2. Wählen Sie die heruntergeladene `.zip`-Datei
3. Überprüfen Sie die Vorschau
4. Wählen Sie die Strategie **Merge** oder **Replace**
5. Tippen Sie auf **Importieren**

!!! note
    Sowohl Benutzer- als auch Assistenten-Nachrichten werden importiert. Nachrichtenrollen bleiben erhalten.

---

## Dateiformat

Die `.橘子岛`-Datei ist ein JSON-basiertes Archiv. Wenn Sie technisch versiert sind, können Sie es mit Standardwerkzeugen inspizieren oder verarbeiten. Das Format ist auf Vorwärts- und Rückwärtskompatibilität ausgelegt.

---

## Automatische Sicherung

橘子岛 kann Ihre Daten automatisch nach einem Zeitplan sichern. Sie müssen nicht daran denken zu exportieren — 橘子岛 erledigt das für Sie.

### Wie es funktioniert

- Die automatische Sicherung läuft regelmäßig im Hintergrund mit Android WorkManager
- Wenn eine Sicherung fällig ist, exportiert 橘子岛 Ihre ausgewählten Kategorien in das konfigurierte Verzeichnis
- Eine Benachrichtigung erscheint nur, wenn eine Sicherung fehlschlägt — erfolgreiche Sicherungen sind still
- Alte Sicherungen werden automatisch basierend auf Ihren Aufbewahrungseinstellungen gelöscht

### Konfiguration

1. Gehen Sie zu **Einstellungen → Datenkontrolle → Automatische Sicherung**
2. Schalten Sie **Automatische Sicherung** ein/aus
3. Legen Sie **Sichern alle** fest — wählen Sie 1 Tag, 3 Tage, 5 Tage, 1 Woche oder 1 Monat
4. Wählen Sie **Exportinhalt** — wählen Sie, welche Kategorien enthalten sein sollen. API-Keys **können** enthalten sein (eine Warnung wird angezeigt, wenn Sie dieses Kästchen aktivieren) — aktivieren Sie es nur, wenn der Sicherungsort privat und sicher ist. API-Keys sind standardmäßig **nicht** enthalten.
5. Legen Sie den **Sicherungsort** fest — tippen Sie, um einen Ordner auszuwählen (Standard ist `Download/橘子岛/Backup`)
6. Schalten Sie **Alte Sicherungen automatisch löschen** ein/aus und legen Sie den Zeitraum **Löschen älter als** fest

!!! info "Einschränkung der automatischen Löschung"
    Der Löschzeitraum muss länger sein als der Sicherungszeitraum. Wenn Sie beispielsweise jede Woche sichern, können Sicherungen nach 1 Monat oder 1 Jahr automatisch gelöscht werden — niemals früher. Dies verhindert das Löschen Ihrer einzigen Sicherung, bevor eine neue erstellt wird.

!!! note
    Die automatische Sicherung verwendet Androids WorkManager, um Zuverlässigkeit auch dann zu gewährleisten, wenn die App geschlossen ist oder das Gerät neu startet. Sicherungen können während des Doze-Modus leicht verzögert sein, um Akku zu sparen.

---

## Bewährte Praktiken

- **Regelmäßig exportieren** als Sicherung — bewahren Sie die Datei an einem sicheren Ort auf
- **Automatische Sicherung aktivieren** für automatischen geplanten Schutz
- **Keine API-Keys in Routinesicherungen einschließen** — Key-Export nur für vollständige Gerätemigrationen aktivieren
- **Merge für inkrementelle Importe verwenden** — Replace ist destruktiv
- **Vor dem Importieren vorschauen** — überprüfen Sie Exportdatum und Inhaltsanzahlen, um zu bestätigen, dass es die richtige Datei ist
