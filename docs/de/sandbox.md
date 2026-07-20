# Sandbox

橘子岛 kann eine leichtgewichtige Alpine Linux-Umgebung lokal auf Ihrem Gerät ausführen — keine Internetverbindung erforderlich. Die Sandbox ermöglicht es dem Modell, Pakete zu installieren und Befehle in einem isolierten Root-Dateisystem auszuführen.

!!! note "Verfügbarkeit"
    Die Sandbox ist in allen Builds verfügbar. Zugriff über **Einstellungen → Shell → Sandbox-Verwaltung** oder direkt über **Einstellungen → Sandbox**.

## Wie es funktioniert

Die Sandbox verwendet ein Alpine Linux Root-Dateisystem, das im app-privaten Speicher Ihres Geräts bereitgestellt wird. Ein minimaler `apk`-basierter Paketmanager ermöglicht die Installation von Software in dieser Umgebung, und Befehle werden in einem proot-basierten Container ausgeführt.

Dies ist **keine** vollständige virtuelle Maschine — es ist ein leichtgewichtiger Userspace-Container, der den Host-Kernel teilt. Er bietet ausreichend Isolation für sicheres Experimentieren bei geringer Ressourcennutzung.

---

## VPN-Interferenz — Kritisch

!!! danger "Schalten Sie Ihr VPN aus, bevor Sie Sandbox-Netzwerke verwenden"

    VPN-Anwendungen stören die DNS-Auflösung von proot. Hier ist der Grund:

    **Ursache — PRoot hat keine Netzwerk-Namespace-Isolation.**

    PRoot verwendet `ptrace`, um Syscalls abzufangen und Dateipfade umzuleiten, aber es unterstützt **kein** `CLONE_NEWNET` (Linux-Netzwerk-Namespaces). Alle Prozesse innerhalb der Sandbox teilen den Netzwerkstack des Host-Android-Systems direkt. Es gibt kein virtuelles Netzwerkinterface, keine isolierte Routing-Tabelle und keine unabhängige DNS-Konfiguration.

    **Wie ein VPN auf Android DNS innerhalb von proot beschädigt:**

    1. Android-VPN-Apps verwenden die `VpnService`-API, die ein **TUN-Interface** erstellt — ein virtuelles Netzwerkgerät, das **gesamten** Geräteverkehr abfängt, einschließlich Verkehr aus proot
    2. Um DNS-Lecks außerhalb des verschlüsselten Tunnels zu verhindern, leitet das VPN **gesamten Port-53 (DNS)-Verkehr** an seine eigenen DNS-Server um
    3. Innerhalb von proot, wenn eine Anwendung `getaddrinfo()` aufruft (den standardmäßigen libc-DNS-Resolver), geht die Anfrage durch den System-Resolver von Android — den das VPN bereits abgefangen hat
    4. Auf Android 12+ hat Google den DNS-Resolver überarbeitet, was `getaddrinfo()` innerhalb von proot-Umgebungen besonders anfällig macht ([termux/proot#215](https://github.com/termux/proot/issues/215))
    5. Das TUN-Routing des VPN und der DNS-Pfad des System-Resolvers kollidieren innerhalb von proot: Der Resolver sendet eine DNS-Anfrage, das VPN-TUN fängt sie ab, aber die Antwort erreicht nie die `ptrace`-Schicht von proot

    **Beobachtete Symptome:**

    | Operation | Ergebnis |
    |-----------|--------|
    | `ping 1.1.1.1` | ✅ Funktioniert (direkte IP, kein DNS nötig) |
    | `ping google.com` | ❌ Fehlgeschlagen — "Temporary failure in name resolution" |
    | `apk add python3` | ❌ Fehlgeschlagen — kann `dl-cdn.alpinelinux.org` nicht auflösen |
    | `curl https://example.com` | ❌ Fehlgeschlagen — Namensauflösungsfehler |
    | `curl https://1.1.1.1` | ✅ Funktioniert (IP-Direktverbindung) |

    **Behebung:** Schalten Sie Ihr VPN vollständig aus, bevor Sie Netzwerkoperationen in der Sandbox durchführen (Pakete installieren, `curl`, `wget`, etc.). Sie können das VPN nach Abschluss der Netzwerkoperationen wieder aktivieren.

    Dies ist eine grundlegende Einschränkung der proot-Architektur — sie kann den Netzwerkstack nicht virtualisieren, wenn ein Android-VPN das DNS-Routing des Systems über ein TUN-Interface überschreibt.

---

## Einrichtung

### Root-Dateisystem installieren

Wenn Sie die Sandbox zum ersten Mal öffnen, sehen Sie ein Dashboard, das anzeigt, dass das Rootfs nicht installiert ist. Tippen Sie auf **Installieren**, um das Alpine Root-Dateisystem herunterzuladen und zu extrahieren.

!!! info "Speicherverbrauch"
    Das Basis-Rootfs verbraucht ungefähr 100–200 MB. Installierte Pakete verbrauchen zusätzlichen Speicherplatz. Die gesamte Speicherplatznutzung wird auf dem Dashboard angezeigt.

---

## Paketverwaltung

### Ein Paket installieren

1. Geben Sie den Paketnamen in das Textfeld ein (z.B. `python3`)
2. Tippen Sie auf **Installieren**
3. Beobachten Sie die Terminal-Ausgabe für den Installationsfortschritt

Alternativ tippen Sie auf einen der **Schnellinstallations-Chips** für gängige Pakete:

```
python3   git      curl      wget
openssh   nodejs   build-base   htop
```

### Installierte Pakete

Unterhalb des Installationsbereichs werden alle installierten Pakete aufgelistet mit:

- **Name** — der Alpine-Paketname
- **Version** — die installierte Version
- **Beschreibung** — eine kurze Zusammenfassung (gekürzt)

### Ein Paket entfernen

Tippen Sie auf das :material-close:-Symbol bei einem installierten Paket, um es zu entfernen. Ein Bestätigungsdialog erscheint vor der Löschung.

---

## Dashboard

Wenn die Sandbox bereit ist, zeigt das Dashboard:

- **Speicherplatznutzung** — ein Fortschrittsbalken und numerische Anzeige (MB oder GB)
- **Installierte Anzahl** — Gesamtzahl der Pakete

---

## Terminal-Ausgabe

Beim Installieren oder Entfernen von Paketen erscheint die Terminal-Ausgabe in einer dunklen, scrollbaren Monospace-Ansicht unterhalb des Eingabefelds. Die Ausgabe scrollt automatisch, um den neuesten Zeilen zu folgen.

Verwenden Sie dies zum:
- Überwachen des Installationsfortschritts
- Debuggen fehlgeschlagener Paketoperationen
- Sehen, welche Dateien ein Paket installiert

---

## Sandbox zurücksetzen

Die **Gefahrenzone** unten enthält eine **Sandbox zurücksetzen**-Option. Dies entfernt das Root-Dateisystem und alle installierten Pakete vollständig.

!!! danger "Destruktive Aktion"
    Das Zurücksetzen der Sandbox löscht die gesamte Alpine-Umgebung. Sie müssen das Rootfs und alle Pakete danach neu installieren. Ein Bestätigungsdialog verhindert versehentliches Zurücksetzen.
