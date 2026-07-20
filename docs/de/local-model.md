# Lokale Modelle

Führen Sie LLMs direkt auf Ihrem Android-Gerät mit GGUF-Modelldateien und llama.cpp aus. Kein Netzwerk erforderlich, keine API-Keys, vollständig privat.

## Wie es funktioniert

橘子岛 bündelt llama.cpp via Android NDK (CMake). Wenn Sie eine GGUF-Datei importieren, läuft das Modell vollständig auf der CPU Ihres Geräts — keine Daten verlassen das Gerät.

## Voraussetzungen

- Nur Modelle im **GGUF-Format** (der Standard für llama.cpp)
- **Gerätespeicher**: Das Modell muss in den verfügbaren RAM passen. Als Faustregel:
    - 1–3B Parameter-Modelle: 4–6 GB RAM
    - 7–8B Parameter-Modelle: 6–8 GB RAM
- **Speicherplatz**: GGUF-Dateien reichen von ~500 MB (quantisierte kleine Modelle) bis 5+ GB

!!! warning
    Lokale Inferenz ist CPU-intensiv und viel langsamer als Cloud-APIs. Sie eignet sich am besten für Offline-Nutzung, datenschutzsensible Inhalte oder Experimente — nicht für schnellen, hochvolumigen Chat.

---

## Ein Chat-Modell importieren

1. Laden Sie eine GGUF-Modelldatei auf Ihr Gerät herunter (siehe empfohlene Quellen unten)
2. Gehen Sie zu **Einstellungen → Provider**
3. Wählen Sie **Lokal** als Provider
4. Tippen Sie auf **GGUF-Modell importieren**
5. Wählen Sie die `.gguf`-Datei von Ihrem Gerät
6. Konfigurieren Sie das Modell:

| Parameter | Beschreibung | Beispiel |
|-----------|-------------|---------|
| **Modell-ID** | Kleinbuchstaben-Bezeichner, keine Leerzeichen | `qwen3-8b` |
| **Alias** | Anzeigename | `Qwen 3 8B` |
| **Kontextgröße** | Maximales Kontextfenster in Tokens | `4096` |
| **Temperature** | Zufälligkeit (0,0–2,0) | `0,7` |
| **Top P** | Nucleus-Sampling-Schwelle (0,0–1,0) | `0,9` |
| **Max Tokens** | Maximale Generierungslänge | `2048` |

7. Tippen Sie auf **Hinzufügen**

Das Modell ist importiert und sofort einsatzbereit.

---

## Ein Embedding-Modell importieren

Embedding-Modelle sind kleiner und werden für die semantische Suche verwendet:

1. Gehen Sie zu **Einstellungen → Konversationssuche**
2. Tippen Sie auf **Lokales Modell hinzufügen**
3. Wählen Sie eine `.gguf`-Embedding-Modelldatei
4. Geben Sie ihm einen Namen
5. Tippen Sie auf **Hinzufügen**

Siehe [Embedding / RAG](embedding.md) für die Sucheinrichtung.

---

## Das aktive Modell auswählen

Nach dem Import eines oder mehrerer Modelle:

1. Gehen Sie zu **Einstellungen → Provider → Lokal**
2. Sie sehen alle importierten Modelle aufgelistet
3. Tippen Sie auf den **Radio-Button** neben dem Modell, das Sie verwenden möchten
4. Das ausgewählte Modell wird aktiv, wenn **Lokal** als Chat-Provider gewählt ist

---

## Lokale Modelle verwalten

### Umbenennen

Tippen Sie auf ein Modell, um seinen Alias zu ändern oder Parameter anzupassen (Temperature, Kontextgröße, etc.).

### Löschen

Langer Druck auf ein Modell und tippen Sie auf **Löschen**. Dies entfernt das Modell aus 橘子岛 und löscht die GGUF-Datei aus dem Speicher.

---

## Empfohlene Modelle

### Chat-Modelle

| Modell | Größe | RAM benötigt | Hinweise |
|-------|------|------------|-------|
| Qwen 3 1.7B | ~1 GB | 3–4 GB | Gute Qualität für seine Größe |
| Llama 3.2 3B | ~2 GB | 4–5 GB | Solider Allrounder |
| Qwen 3 8B | ~5 GB | 7–8 GB | Beste Qualität, hoher RAM |

### Embedding-Modelle

| Modell | Größe | Hinweise |
|-------|------|-------|
| BGE Small EN v1.5 | ~130 MB | Gute englische Embeddings |
| BGE Small ZH v1.5 | ~130 MB | Chinesisch-optimiert |
| Nomic Embed Text v1.5 | ~270 MB | Gut mehrsprachig |

### Wo man GGUF-Dateien bekommt

- [Hugging Face](https://huggingface.co/models?library=gguf) — suchen Sie nach "GGUF"
- [bartowskis quantisierte Modelle](https://huggingface.co/bartowski) — breite Auswahl, gut organisiert

!!! tip
    Suchen Sie nach `Q4_K_M`-Quantisierung — sie bietet den besten Kompromiss zwischen Qualität und Größe für Chat-Modelle.

---

## Leistungstipps

- **Kleinerer Kontext = schneller**: Beginnen Sie mit 2048 und erhöhen Sie nur bei Bedarf
- **Niedrigere Quant = schneller**: Q4_K_M ist schneller als Q6_K oder Q8
- **Andere Apps schließen**: Lokale Inferenz benötigt so viel RAM wie möglich
- **Netzteil anschließen**: Inferenz ist CPU-intensiv und längere Nutzung entlädt den Akku
