# Proxy de Red

Enruta todo el tráfico de red de 橘子岛 a través de un proxy HTTP o SOCKS. Útil en redes restringidas, para enrutar solicitudes a través de una puerta de enlace específica, o cuando un proveedor solo es accesible mediante un proxy.

El proxy se aplica a **todo** el tráfico saliente: proveedores de chat, obtención de modelos, búsqueda web, embeddings, obtención de páginas web y envío de informes de fallos.

## Configuración

Abre **Ajustes → Red → Proxy** y activa **Habilitar proxy**, luego configura:

| Campo | Descripción |
|-------|-------------|
| **Tipo** | `HTTP`, `HTTPS` o `SOCKS5`. HTTP/HTTPS tunelizan el tráfico HTTPS a través de `CONNECT`; SOCKS5 reenvía a nivel de socket. |
| **Host** | Nombre de host o IP del servidor proxy (ej. `127.0.0.1`). |
| **Puerto** | Puerto del servidor proxy (ej. `7890`). |
| **Usuario / Contraseña** | Opcional. Solo necesario si tu proxy requiere autenticación. |

Los cambios surten efecto inmediatamente — no es necesario reiniciar la aplicación.

## Lista de omisión

Los hosts y rangos de direcciones en la **lista de omisión** se conectan **directamente**, ignorando el proxy. Pon una entrada por línea. La lista predeterminada mantiene las direcciones de bucle local y privadas (LAN) directas:

```
localhost
127.0.0.1
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
::1
```

Cada línea puede ser:

- un host exacto — `localhost`, `192.168.1.10`
- un rango IPv4 CIDR — `10.0.0.0/8`
- un sufijo comodín — `*.example.com`

Por eso un servidor Ollama local (ej. `http://192.168.1.50:11434`) sigue funcionando en tu LAN mientras todo lo demás pasa por el proxy.

## Notas

- El tipo **HTTPS** usa el mismo protocolo de proxy que HTTP (un proxy HTTP `CONNECT`); elígelo si tu proxy está etiquetado como "HTTPS".
- La contraseña del proxy se incluye en las **exportaciones de datos cifradas solo cuando "Incluir claves API" está habilitado**.
- Si las solicitudes fallan con tiempos de espera después de habilitar el proxy, verifica el host/puerto y que el tipo de proxy coincida con tu servidor.
