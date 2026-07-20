# Bildgenerierung

Generieren Sie Bilder aus Text-Prompts mit einem Text-zu-Bild-Modell, direkt in Ihren Konversationen.

## Was es macht

Wenn die Bildgenerierung aktiviert ist, kann 橘子岛 Ihre Prompts mit einem dedizierten Text-zu-Bild-Modell (wie DALL·E, GPT-Image, Imagen, FLUX, Stable Diffusion, Seedream, Qwen-Image und vielen anderen) in Bilder umwandeln. Das generierte Bild wird in die Konversation zurückgegeben, sodass Sie wie bei jeder anderen Antwort darauf iterieren können.

Die Bildgenerierung verwendet eine **eigene Modellauswahl**, unabhängig vom Modell, mit dem Sie chatten — Sie können also mit einem Modell chatten und mit einem anderen Bilder generieren.

## Einrichtung

1. Gehen Sie zu **Einstellungen → Bildgenerierung**
2. Schalten Sie **Bildgenerierung aktivieren** ein
3. Tippen Sie auf **Modell** und wählen Sie ein Text-zu-Bild-Modell
4. Legen Sie optional die **Standardgröße** fest (Breite × Höhe)

!!! note "BYOK — dedizierte Anmeldeinformationen"
    Die Bildgenerierung verwendet ihren **eigenen dedizierten API-Key und Basis-URL**, unabhängig von Ihren Chat-Providern. Das bedeutet, Sie können einen anderen Dienst für Bilder als für den Chat verwenden. Gehen Sie zu **Einstellungen → Bildgenerierung**, um Key, Basis-URL, Modell und Standardgröße zu konfigurieren.

## Modellauswahl

Tippen Sie auf **Modell**, um das für die Generierung verwendete Modell auszuwählen.

- Die Auswahl zeigt Modelle, die wie Text-zu-Bild-Modelle aussehen, gefiltert aus all Ihren synchronisierten Modellen, sodass die Liste kurz bleibt.
- Wenn das gewünschte Modell nicht aufgelistet ist (ein ungewöhnlicher Name), aktivieren Sie **Alle Modelle anzeigen**, um aus der vollständigen Liste zu wählen.
- Nur ein ordnungsgemäß synchronisierter `Provider:Modell`-Eintrag zählt als gültige Auswahl. Synchronisieren Sie Ihre Modelle zuerst unter **Einstellungen → API-Provider** / **Modelle verwalten**, wenn die Liste leer ist.

## Standardgröße

Legt die Standard-Ausgabeabmessungen fest, eingegeben als **Breite × Höhe** in Pixeln (zum Beispiel `1024` × `1024`).

- Der Standard ist `1024 × 1024`.
- Unterstützte Größen hängen vom Modell und Provider ab — wenn ein Modell eine Größe ablehnt, versuchen Sie einen Wert, den es dokumentiert (gängige Optionen sind `1024×1024`, `1024×1792`, `1792×1024`).

## Wie es funktioniert

1. Aktivieren Sie die Bildgenerierung und wählen Sie ein Bildmodell
2. Bitten Sie den Assistenten in einer Konversation, ein Bild zu erstellen
3. 橘子岛 leitet die Anfrage an das konfigurierte Bildmodell unter Verwendung der Anmeldeinformationen dieses Providers weiter
4. Das generierte Bild wird in die Konversation zurückgegeben

!!! tip
    Seien Sie spezifisch in Ihrem Prompt — beschreiben Sie das Motiv, den Stil, die Komposition und die Beleuchtung. Klare Prompts liefern weit bessere Ergebnisse als vage.
