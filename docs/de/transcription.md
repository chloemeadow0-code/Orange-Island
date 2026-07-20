# Bildtranskription

Lassen Sie ein Vision-Modell Bilder beschreiben, damit textbasierte Modelle sie verstehen können.

## Was es macht

Wenn Sie ein Bild an ein textbasiertes Modell senden, kann 橘子岛 ein separates Vision-Modell verwenden, um zuerst eine Textbeschreibung des Bildes zu generieren. Diese Beschreibung wird dann in den Prompt eingefügt, der an Ihr Hauptmodell gesendet wird.

So können Sie Bilder mit jedem Modell verwenden, auch mit solchen, die nativ kein Vision unterstützen.

## Einrichtung

1. Gehen Sie zu **Einstellungen → Bildtranskription**
2. Wählen Sie ein **Transkriptionsmodell** — dies sollte ein vision-fähiges Modell sein (z.B. GPT-4o, Gemini Flash, Qwen-VL)
3. Fügen Sie Modelle zu **Aktivierte Modelle** hinzu — dies sind die textbasierten Modelle, die Bildbeschreibungen erhalten
4. Passen Sie die **Batch-Größe** an, wenn Sie viele Bilder auf einmal senden (wie viele Bilder pro API-Aufruf beschrieben werden)

!!! tip "Lokale Vision-Modelle"
    Sie können ein lokales Vision-Modell (mit mmproj) als Transkriptionsmodell verwenden. Dies hält die Bildverarbeitung auf dem Gerät.

## Wie es funktioniert

1. Sie hängen ein Bild an Ihre Nachricht an
2. 橘子岛 erkennt, dass Ihr aktuelles Modell kein Vision unterstützt
3. Das Bild wird zuerst an das Transkriptionsmodell gesendet
4. Das Transkriptionsmodell generiert eine Textbeschreibung
5. Diese Beschreibung wird Ihrem Nachrichtentext vorangestellt
6. Der kombinierte Text wird an Ihr Hauptmodell gesendet

---

## Batch-Größe

Steuert, wie viele Bilder pro API-Aufruf an das Transkriptionsmodell beschrieben werden.

- **1** — Ein Bild nach dem anderen beschreiben (mehr API-Aufrufe, genauer)
- **5–10** — Mehrere Bilder pro Aufruf beschreiben (weniger API-Aufrufe, kann Details verlieren)

Der Standard ist geräteabhängig. Niedrigere Werte liefern bessere Ergebnisse, kosten aber mehr.

---

## Modellauswahl

### Transkriptionsmodell

Dies ist das Vision-Modell, das Bildbeschreibungen generiert. Wählen Sie das leistungsfähigste Vision-Modell, das Ihnen zur Verfügung steht.

### Aktivierte Modelle

Dies sind die textbasierten Modelle, die Bildtranskription verwenden. Nur Modelle in dieser Liste erhalten transkribierte Bildbeschreibungen. Andere Modelle erhalten Bilder direkt (wenn sie sie unterstützen) oder gar nicht.
