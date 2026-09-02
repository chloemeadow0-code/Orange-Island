# Erste Schritte

Diese Anleitung führt Sie durch die Installation von 橘子岛, das Hinzufügen Ihres ersten API-Keys und das Senden Ihrer ersten Nachricht.

## Installation

### Von F-Droid (Empfohlen)

橘子岛 ist auf F-Droid verfügbar, dem Open-Source Android-App-Store.

1. Installieren Sie [F-Droid](https://f-droid.org/) auf Ihrem Gerät
2. Öffnen Sie F-Droid, suchen Sie nach **橘子岛**
3. Tippen Sie auf **Installieren**

### Von GitHub Releases

1. Besuchen Sie die [Releases-Seite](https://github.com/chloemeadow0-code/Orange-Island/releases)
2. Laden Sie die neueste `.apk`-Datei herunter
3. Öffnen Sie die Datei auf Ihrem Gerät und bestätigen Sie die Installation bei Aufforderung

### Aus dem Quellcode bauen

Wenn Sie lieber selbst bauen möchten:

1. Klonen Sie das Repository:
   ```
   git clone https://github.com/chloemeadow0-code/Orange-Island.git
   ```
2. Öffnen Sie das Projekt in [Android Studio](https://developer.android.com/studio) (Ladybug oder neuer)
3. Synchronisieren Sie Gradle und bauen Sie

Voraussetzungen: Android SDK 34+, JDK 17+.

---

## Erster Start

Wenn Sie 橘子岛 zum ersten Mal öffnen, sehen Sie einen Willkommensbildschirm mit einer Texteingabe. Bevor Sie chatten können, müssen Sie einen Provider und einen API-Key konfigurieren.

### Schritt 1: Einen API-Key hinzufügen

1. Tippen Sie auf das **Einstellungen**-Symbol (Zahnrad unten rechts) in der Navigationsleiste
2. Unter **Dienste** tippen Sie auf **Provider**
3. Wählen Sie einen Provider aus der Liste (z.B. **OpenAI**, **Anthropic**, **Google**)
4. Tippen Sie auf **Neuen Key hinzufügen**
5. Geben Sie einen Namen für Ihren Key ein (z.B. "Persönlich") und fügen Sie Ihren API-Key ein
6. Tippen Sie auf **Hinzufügen**

??? tip "Wo bekomme ich einen API-Key?"
    - **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — kostenloses Kontingent verfügbar
    - **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic**: [Console API Keys](https://console.anthropic.com/)
    - **DeepSeek**: [Platform](https://platform.deepseek.com/)
    - **OpenRouter**: [Keys-Seite](https://openrouter.ai/keys)

    Siehe die [API-Provider](provider.md)-Seite für Details zu jedem Provider.

### Schritt 2: Modelle synchronisieren

1. Gehen Sie zurück zu den Einstellungen und tippen Sie auf **Modelle** (unter **Dienste**)
2. Tippen Sie auf **Von allen Providern synchronisieren**
3. 橘子岛 ruft die neueste Modellliste für alle konfigurierten Provider ab
4. Nach der Synchronisierung tippen Sie auf ein Modell, um es als Ihr **Standardmodell** festzulegen

### Schritt 3: Ihre erste Nachricht senden

1. Tippen Sie auf den **Zurück-Pfeil**, um zum Chat-Bildschirm zurückzukehren
2. Geben Sie eine Nachricht in das Eingabefeld unten ein
3. Tippen Sie auf **Senden** (Papierflieger-Symbol)

Das Modell streamt seine Antwort in Echtzeit.

---

## App-Layout

橘子岛 hat ein klares Layout, das sich um den Chat-Bildschirm dreht:

### Obere Leiste

- **Konversationstitel** — zeigt den aktuellen Konversationsnamen an (tippen zum Umbenennen)
- **Hamburger-Menü** (:material-menu:) — öffnet die Konversationsleiste
- **Overflow-Menü** (:material-dots-vertical:) — konversationsbezogene Einstellungen (Modell, System-Prompt, Generierungsparameter)

### Konversationsleiste

Tippen Sie auf das **Hamburger-Menü** oder wischen Sie vom linken Rand nach rechts, um sie zu öffnen:

- **Suchleiste** — vergangene Konversationen per Schlüsselwort oder semantischer Suche finden
- **Konversationsliste** — alle Konversationen, neueste zuerst
- **Einstellungen** (:material-cog:) — Provider, Modelle, Prompts und mehr konfigurieren
- **Neuer Chat** — eine neue Konversation starten

### Chat-Bildschirm

- **Nachrichtenbereich** — scrollbare Konversationshistorie mit Markdown-Darstellung
- **Untere Leiste** — Texteingabe, Modellauswahl, Anhang-Button (+) und Senden-Button

---

## Nächste Schritte

- [System-Prompts konfigurieren](system-prompts.md), um das Modellverhalten anzupassen
- [Websuche einrichten](web-search.md) für Live-Internetzugriff
- [Agentische Tools erkunden](tools.md) — Shell-Ausführung, Dateioperationen und Speicher
- [Daten importieren](import-export.md) von Claude oder ChatGPT
- [Lokale Modelle ausführen](local-model.md) für Offline-Nutzung
