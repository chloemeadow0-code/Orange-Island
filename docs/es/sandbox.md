# Sandbox

橘子岛 puede ejecutar un entorno ligero de Alpine Linux localmente en tu dispositivo — sin necesidad de conexión a internet. El sandbox permite al modelo instalar paquetes y ejecutar comandos en un sistema de archivos raíz aislado.

!!! note "Disponibilidad"
    El sandbox está disponible en todas las compilaciones. Accede desde **Configuración → Shell → Gestión del Sandbox** o directamente desde **Configuración → Sandbox**.

## Cómo Funciona

El sandbox utiliza un sistema de archivos raíz de Alpine Linux desplegado en el almacenamiento privado de la aplicación de tu dispositivo. Un gestor de paquetes mínimo basado en `apk` te permite instalar software en este entorno, y los comandos se ejecutan dentro de un contenedor basado en proot.

Esto **no** es una máquina virtual completa — es un contenedor ligero en espacio de usuario que comparte el kernel del host. Proporciona suficiente aislamiento para experimentación segura mientras mantiene bajo el uso de recursos.

---

## Interferencia de VPN — Crítico

!!! danger "Apaga Tu VPN Antes de Usar la Red del Sandbox"

    Las aplicaciones VPN interfieren con la resolución DNS de proot. Este es el motivo:

    **Causa raíz — PRoot no tiene aislamiento de espacio de nombres de red.**

    PRoot usa `ptrace` para interceptar llamadas al sistema y redirigir rutas de archivos, pero **no** soporta `CLONE_NEWNET` (espacios de nombres de red de Linux). Todos los procesos dentro del sandbox comparten la pila de red del sistema Android host directamente. No hay interfaz de red virtual, ni tabla de enrutamiento aislada, ni configuración DNS independiente.

    **Cómo una VPN en Android rompe el DNS dentro de proot:**

    1. Las aplicaciones VPN de Android usan la API `VpnService`, que crea una **interfaz TUN** — un dispositivo de red virtual que intercepta **todo** el tráfico del dispositivo, incluyendo el tráfico desde dentro de proot
    2. Para prevenir fugas de DNS fuera del túnel cifrado, la VPN **redirige todo el tráfico del puerto 53 (DNS)** a sus propios servidores DNS
    3. Dentro de proot, cuando una aplicación llama a `getaddrinfo()` (el resolvedor DNS estándar de libc), la solicitud pasa a través del resolvedor del sistema de Android — que la VPN ya ha interceptado
    4. En Android 12+, Google rediseñó el resolvedor DNS, haciendo que `getaddrinfo()` dentro de entornos proot sea particularmente frágil ([termux/proot#215](https://github.com/termux/proot/issues/215))
    5. El enrutamiento TUN de la VPN y la ruta DNS del resolvedor del sistema entran en conflicto dentro de proot: el resolvedor envía una consulta DNS, la TUN de la VPN la intercepta, pero la respuesta nunca llega de vuelta a través de la capa `ptrace` de proot

    **Síntomas observados:**

    | Operación | Resultado |
    |-----------|-----------|
    | `ping 1.1.1.1` | ✅ Funciona (IP directa, no necesita DNS) |
    | `ping google.com` | ❌ Falla — "Fallo temporal en la resolución de nombres" |
    | `apk add python3` | ❌ Falla — no puede resolver `dl-cdn.alpinelinux.org` |
    | `curl https://example.com` | ❌ Falla — error de resolución de nombres |
    | `curl https://1.1.1.1` | ✅ Funciona (conexión directa por IP) |

    **Solución:** Apaga tu VPN completamente antes de realizar cualquier operación de red en el sandbox (instalar paquetes, `curl`, `wget`, etc.). Puedes volver a activar la VPN después de que las operaciones de red terminen.

    Esta es una limitación fundamental de la arquitectura de proot — no puede virtualizar la pila de red cuando una VPN de Android anula el enrutamiento DNS del sistema a través de una interfaz TUN.

---

## Configuración

### Instalar el Sistema de Archivos Raíz

La primera vez que abras el sandbox, verás un panel indicando que el rootfs no está instalado. Toca **Instalar** para descargar y extraer el sistema de archivos raíz de Alpine.

!!! info "Uso de Almacenamiento"
    El rootfs base usa aproximadamente 100–200 MB. Los paquetes instalados consumen espacio adicional. El uso total de disco se muestra en el panel.

---

## Gestión de Paquetes

### Instalar un Paquete

1. Escribe el nombre del paquete en el campo de texto (ej., `python3`)
2. Toca **Instalar**
3. Observa la salida del terminal para el progreso de la instalación

Alternativamente, toca cualquier **chip de instalación rápida** para paquetes comunes:

```
python3   git      curl      wget
openssh   nodejs   build-base   htop
```

### Paquetes Instalados

Debajo de la sección de instalación, todos los paquetes instalados se listan con su:

- **Nombre** — el nombre del paquete Alpine
- **Versión** — la versión instalada
- **Descripción** — un breve resumen (truncado)

### Eliminar un Paquete

Toca el icono :material-close: en cualquier paquete instalado para eliminarlo. Aparece un diálogo de confirmación antes de la eliminación.

---

## Panel de Control

Cuando el sandbox está listo, el panel muestra:

- **Uso de disco** — una barra de progreso y visualización numérica (MB o GB)
- **Cantidad instalada** — número total de paquetes

---

## Salida del Terminal

Al instalar o eliminar paquetes, la salida del terminal aparece en una vista de monotipo desplazable con tema oscuro debajo del campo de entrada. La salida se desplaza automáticamente para seguir las últimas líneas.

Usa esto para:
- Monitorizar el progreso de la instalación
- Depurar operaciones de paquetes fallidas
- Ver qué archivos instala un paquete

---

## Restablecer Sandbox

La **Zona de Peligro** en la parte inferior contiene una opción de **Restablecer Sandbox**. Esto elimina completamente el sistema de archivos raíz y todos los paquetes instalados.

!!! danger "Acción Destructiva"
    Restablecer el sandbox elimina todo el entorno Alpine. Necesitarás reinstalar el rootfs y todos los paquetes después. Un diálogo de confirmación previene restablecimientos accidentales.
