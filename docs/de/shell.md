# Remote Shell (Conch)

橘子岛 kann Befehle auf entfernten Rechnern über das [Conch](https://github.com/newo-ether/conch)-Protokoll ausführen — eine Ende-zu-Ende-verschlüsselte, sichere Shell, die für KI-Agenten entwickelt wurde.

## Wie es funktioniert

```text
橘子岛 (Android)  ──ECDH + AES-256-GCM──▶  Conch Server (Linux/macOS/Windows)
                                           │
                                           ├── Befehle ausführen
                                           ├── Dateien lesen/schreiben/bearbeiten
                                           ├── Glob- und Grep-Suche
                                           └── Ergebnisse zurückgeben
```

Das Modell entscheidet, wann es die Shell verwendet — es kann Serverstatus prüfen, Dateien verwalten, Skripte ausführen oder Probleme autonom beheben.

## Sicherheit

Conch verwendet starke Verschlüsselung und Anti-Missbrauch-Schutz:

- **ECDH-Schlüsselaustausch** — flüchtige Schlüssel pro Sitzung
- **AES-256-GCM-Verschlüsselung** — gesamter Datenverkehr verschlüsselt
- **HMAC-SHA256-Signierung** — Nachrichtenintegrität verifiziert
- **Token-Bucket-Ratenbegrenzung** — verhindert Missbrauch
- **Nonce-basierter Anti-Replay-Schutz** — jede Anfrage ist einzigartig

!!! note
    Befehle werden mit den Berechtigungen des Benutzers ausgeführt, der den Conch-Server betreibt. Verwenden Sie ein eingeschränktes Benutzerkonto für sensible Umgebungen.

---

## Einrichtung

### Schritt 1: Conch-Server bereitstellen

Stellen Sie den Conch-Server auf Ihrem Zielrechner bereit. Siehe das [Conch-Repository](https://github.com/newo-ether/conch) für Einrichtungsanweisungen.

### Schritt 2: Gerät in 橘子岛 hinzufügen

1. Gehen Sie zu **Einstellungen → Shell**
2. Aktivieren Sie **Shell-Tool**
3. Tippen Sie auf **Gerät hinzufügen**
4. Wählen Sie den Gerätetyp: **Conch** oder **SSH**
5. Füllen Sie die Gerätedetails aus:

=== "Conch"

    | Feld | Beschreibung | Beispiel |
    |-------|-------------|---------|
    | **Name** | Anzeigename für dieses Gerät | `Build-Server` |
    | **Beschreibung** | Optionale Notiz zu diesem Rechner | `Büro Ubuntu-Rechner` |
    | **Server-URL** | Conch-Server-Endpunkt (Host:Port) | `http://192.168.1.100:14216` |
    | **API-Key** | Authentifizierungstoken | Aus der Conch-Server-Konfiguration |
    | **Timeout** | Befehls-Timeout in Sekunden | `30` |

=== "SSH"

    | Feld | Beschreibung | Beispiel |
    |-------|-------------|---------|
    | **Name** | Anzeigename für dieses Gerät | `VPS-Server` |
    | **Beschreibung** | Optionale Notiz zu diesem Rechner | `Produktions-Webserver` |
    | **Host** | SSH-Hostname oder IP-Adresse | `192.168.1.200` |
    | **Port** | SSH-Port | `22` |
    | **Benutzer** | SSH-Benutzername | `root` |
    | **Passwort** | SSH-Passwort | Ihr SSH-Passwort |

Tippen Sie auf **Hinzufügen** zum Speichern.

### Schritt 3: Verwenden

Einmal konfiguriert, kann das Modell auf das Gerät zugreifen. Es gibt keinen manuellen Auslöser — das Modell erkennt automatisch verfügbare Shell-Geräte und ruft sie bei Bedarf auf.

---

## Multi-Geräte-Unterstützung

Fügen Sie mehrere Shell-Geräte hinzu, damit das Modell maschinenübergreifend arbeiten kann:

- **Build-Server** — Code kompilieren und testen
- **Home Lab** — selbst gehostete Dienste verwalten
- **Entwicklungs-VM** — Code bearbeiten und Skripte ausführen

Jedes Gerät ist unabhängig mit eigenem Namen, URL und Anmeldeinformationen konfiguriert. Das Modell kann zwischen ihnen unterscheiden und das richtige Gerät für jede Aufgabe wählen.

---

## Verfügbare Operationen

### Befehlsausführung (`shell_execute`)

Jeden Shell-Befehl ausführen und stdout, stderr und Exit-Code erhalten.

### Dateioperationen

| Tool | Funktion |
|------|----------|
| `file_read` | Eine Datei aus dem entfernten Dateisystem lesen |
| `file_write` | Eine Datei schreiben oder überschreiben |
| `file_edit` | Exakte Zeichenfolgen-Ersetzungen in einer Datei durchführen |
| `file_glob` | Dateien finden, die einem Glob-Muster entsprechen |
| `file_grep` | Dateiinhalte mit Regex durchsuchen |

Alle Dateioperationen gehen durch den verschlüsselten Conch-Kanal.

---

## MCP-Integration

Conch kann auch als **Claude Desktop MCP-Server** dienen. Wenn Sie Claude Code oder einen anderen MCP-Client verwenden, können Sie Conch als Tool-Provider für entfernten Datei- und Shell-Zugriff von Ihrem Desktop aus konfigurieren.

Siehe die [Conch-Dokumentation](https://github.com/newo-ether/conch) für MCP-Einrichtungsanweisungen.

---

## Problembehandlung

### Gerät wird als nicht verfügbar angezeigt
- Prüfen Sie, ob der Conch-Server läuft
- Überprüfen Sie, ob die URL von Ihrem Android-Gerät aus erreichbar ist
- Überprüfen Sie die Firewall-Regeln auf dem Server

### Befehle laufen in Timeout
- Erhöhen Sie das Timeout in den Geräteeinstellungen
- Prüfen Sie, ob der Befehl hängt (Benutzereingabe erfordert, etc.)

### Authentifizierung schlägt fehl
- Überprüfen Sie, ob der API-Key mit der Server-Konfiguration übereinstimmt
- Generieren Sie Keys bei Bedarf neu
