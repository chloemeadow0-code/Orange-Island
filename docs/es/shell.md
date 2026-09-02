# Shell Remoto (Conch)

橘子岛 puede ejecutar comandos en máquinas remotas a través del protocolo [Conch](https://github.com/newo-ether/conch) — un shell seguro cifrado de extremo a extremo diseñado para agentes de IA.

## Cómo Funciona

```text
橘子岛 (Android)  ──ECDH + AES-256-GCM──▶  Servidor Conch (Linux/macOS/Windows)
                                           │
                                           ├── Ejecutar comandos
                                           ├── Leer/escribir/editar archivos
                                           ├── Búsqueda glob y grep
                                           └── Devolver resultados
```

El modelo decide cuándo usar el shell — puede verificar el estado del servidor, gestionar archivos, ejecutar scripts o solucionar problemas de forma autónoma.

## Seguridad

Conch utiliza cifrado fuerte y protecciones anti-abuso:

- **Intercambio de claves ECDH** — claves efímeras por sesión
- **Cifrado AES-256-GCM** — todo el tráfico cifrado
- **Firma HMAC-SHA256** — integridad de mensajes verificada
- **Limitación de tasa por token bucket** — previene abusos
- **Anti-repetición basada en nonce** — cada solicitud es única

!!! note
    Los comandos se ejecutan con los permisos del usuario que ejecuta el servidor Conch. Usa una cuenta de usuario restringida para entornos sensibles.

---

## Configuración

### Paso 1: Desplegar el Servidor Conch

Despliega el servidor Conch en tu máquina de destino. Consulta el [repositorio de Conch](https://github.com/newo-ether/conch) para instrucciones de configuración.

### Paso 2: Añadir Dispositivo en 橘子岛

1. Ve a **Configuración → Shell**
2. Habilita **Herramienta Shell**
3. Toca **Añadir Dispositivo**
4. Elige el tipo de dispositivo: **Conch** o **SSH**
5. Rellena los detalles del dispositivo:

=== "Conch"

    | Campo | Descripción | Ejemplo |
    |-------|-------------|---------|
    | **Nombre** | Nombre descriptivo para este dispositivo | `Servidor de Compilación` |
    | **Descripción** | Nota opcional sobre esta máquina | `Caja Ubuntu de oficina` |
    | **URL del Servidor** | Endpoint del servidor Conch (host:puerto) | `http://192.168.1.100:14216` |
    | **Clave API** | Token de autenticación | De la configuración del servidor Conch |
    | **Timeout** | Tiempo de espera del comando en segundos | `30` |

=== "SSH"

    | Campo | Descripción | Ejemplo |
    |-------|-------------|---------|
    | **Nombre** | Nombre descriptivo para este dispositivo | `Servidor VPS` |
    | **Descripción** | Nota opcional sobre esta máquina | `Servidor web de producción` |
    | **Host** | Nombre de host SSH o dirección IP | `192.168.1.200` |
    | **Puerto** | Puerto SSH | `22` |
    | **Usuario** | Nombre de usuario SSH | `root` |
    | **Contraseña** | Contraseña SSH | Tu contraseña SSH |

Toca **Añadir** para guardar.

### Paso 3: Usar

Una vez configurado, el modelo puede acceder al dispositivo. No hay un disparador manual — el modelo descubre automáticamente los dispositivos shell disponibles y los llama cuando corresponde.

---

## Soporte Multi-Dispositivo

Añade múltiples dispositivos shell para permitir al modelo trabajar en varias máquinas:

- **Servidor de compilación** — compilar y probar código
- **Laboratorio doméstico** — gestionar servicios autoalojados
- **VM de desarrollo** — editar código y ejecutar scripts

Cada dispositivo se configura independientemente con su propio nombre, URL y credenciales. El modelo puede distinguirlos y elegir el dispositivo adecuado para cada tarea.

---

## Operaciones Disponibles

### Ejecución de Comandos (`shell_execute`)

Ejecuta cualquier comando de shell y recibe stdout, stderr y código de salida.

### Operaciones de Archivos

| Herramienta | Función |
|-------------|---------|
| `file_read` | Leer un archivo del sistema de archivos remoto |
| `file_write` | Escribir o sobrescribir un archivo |
| `file_edit` | Realizar reemplazos exactos de cadenas en un archivo |
| `file_glob` | Encontrar archivos que coincidan con un patrón glob |
| `file_grep` | Buscar contenido de archivos con regex |

Todas las operaciones de archivos pasan por el canal cifrado Conch.

---

## Integración MCP

Conch también puede servir como un **servidor MCP de Claude Desktop**. Si usas Claude Code u otro cliente MCP, puedes configurar Conch como proveedor de herramientas para acceso remoto a archivos y shell desde tu escritorio.

Consulta la [documentación de Conch](https://github.com/newo-ether/conch) para instrucciones de configuración MCP.

---

## Solución de Problemas

### El dispositivo aparece como no disponible
- Verifica que el servidor Conch esté funcionando
- Comprueba que la URL sea accesible desde tu dispositivo Android
- Revisa las reglas del firewall en el servidor

### Los comandos expiran
- Aumenta el timeout en la configuración del dispositivo
- Verifica que el comando no se esté colgando (requiere entrada de usuario, etc.)

### La autenticación falla
- Verifica que la clave API coincida con la configuración del servidor
- Regenera las claves si es necesario
