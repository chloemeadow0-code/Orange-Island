# Netzwerk-Proxy

Den gesamten Netzwerkverkehr von 橘子岛 über einen HTTP- oder SOCKS-Proxy leiten. Nützlich in eingeschränkten Netzwerken, zum Routing über ein bestimmtes Gateway oder wenn ein Anbieter nur über einen Proxy erreichbar ist.

Der Proxy gilt für **den gesamten** ausgehenden Verkehr: Chat-Anbieter, Modellabruf, Websuche, Embeddings, Webseitenabruf und Absturzbericht-Übermittlung.

## Einrichtung

Öffne **Einstellungen → Netzwerk → Proxy** und aktiviere **Proxy aktivieren**, dann konfiguriere:

| Feld | Beschreibung |
|------|-------------|
| **Typ** | `HTTP`, `HTTPS` oder `SOCKS5`. HTTP/HTTPS tunneln HTTPS-Verkehr über `CONNECT`; SOCKS5 leitet auf Socket-Ebene weiter. |
| **Host** | Hostname oder IP des Proxy-Servers (z. B. `127.0.0.1`). |
| **Port** | Port des Proxy-Servers (z. B. `7890`). |
| **Benutzername / Passwort** | Optional. Nur erforderlich, wenn der Proxy Authentifizierung verlangt. |

Änderungen werden sofort wirksam — ein Neustart der App ist nicht erforderlich.

## Umgehungsliste

Hosts und Adressbereiche in der **Umgehungsliste** verbinden sich **direkt** und ignorieren den Proxy. Ein Eintrag pro Zeile. Die Standardliste hält Loopback- und private (LAN) Adressen direkt:

```
localhost
127.0.0.1
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
::1
```

Jede Zeile kann sein:

- ein exakter Host — `localhost`, `192.168.1.10`
- ein IPv4-CIDR-Bereich — `10.0.0.0/8`
- ein Wildcard-Suffix — `*.example.com`

Deshalb funktioniert ein lokaler Ollama-Server (z. B. `http://192.168.1.50:11434`) weiterhin über dein LAN, während alles andere über den Proxy läuft.

## Hinweise

- Der Typ **HTTPS** verwendet dasselbe Proxy-Protokoll wie HTTP (einen HTTP-`CONNECT`-Proxy); wähle ihn, wenn dein Proxy als „HTTPS" gekennzeichnet ist.
- Das Proxy-Passwort ist nur in **verschlüsselten Datenexporten enthalten, wenn „API-Schlüssel einschließen" aktiviert ist**.
- Wenn Anfragen nach Aktivierung des Proxys mit Zeitüberschreitungen fehlschlagen, überprüfe Host/Port und ob der Proxy-Typ zu deinem Server passt.
