# Herramientas Agentivas

Los modelos de 橘子岛 pueden usar herramientas de forma autónoma — deciden qué buscar, ejecutar, leer o recordar sin que necesites activar manualmente cada acción. Las herramientas operan en **bucles de múltiples rondas**: el modelo puede llamar a una herramienta, leer el resultado y luego decidir llamar a otra herramienta o responder.

## Cómo Funciona la Llamada a Herramientas

1. Envías un mensaje
2. El modelo decide que necesita información o acción externa
3. Emite una **llamada a herramienta** — una solicitud estructurada con un nombre de herramienta y argumentos
4. 橘子岛 ejecuta la herramienta en el dispositivo o en un servidor remoto
5. El resultado se devuelve al modelo
6. El modelo puede llamar a otra herramienta o producir una respuesta final

Este bucle puede repetirse múltiples veces dentro de un solo turno de mensaje.

## Herramientas Disponibles

### Búsqueda Web

Busca en internet y obtén páginas web. El modelo puede buscar información actual, verificar hechos o recuperar documentación.

- **Proveedores**: DuckDuckGo Lite (predeterminado, sin clave), Brave, Serper, Tavily, SearXNG
- **Configuración**: Configuración → Búsqueda Web
- **Guía**: [Búsqueda Web](web-search.md)

### Generación de Imágenes

Genera imágenes a partir de prompts de texto usando un modelo dedicado de texto a imagen. Las imágenes se muestran en línea en la conversación y pueden verse a pantalla completa.

- **Proveedor**: BYOK — usa tu propia clave API y URL base, independiente del modelo de chat
- **Configuración**: Configuración → Generación de Imágenes
- **Guía**: [Generación de Imágenes](image-generation.md)

### Ejecución de Código

Ejecuta código en un entorno aislado:

- **Gemini Code Execution** — ejecución de código integrada para modelos Gemini (sin configuración)
- **Sandbox** — entorno local Alpine Linux mediante PRoot, con gestión de paquetes y acceso a archivos SAF

### Shell Remoto

Ejecuta comandos en máquinas remotas a través del protocolo [Conch](https://github.com/orangeisland/conch). El modelo puede verificar el estado del servidor, gestionar archivos o ejecutar scripts.

- **Protocolo**: Cifrado de extremo a extremo (ECDH + AES-256-GCM)
- **Configuración**: Configuración → Shell
- **Guía**: [Shell Remoto](shell.md)

### Operaciones de Archivos

Lee, escribe, edita, busca con glob y busca con grep archivos en dispositivos remotos a través del protocolo Conch. El modelo puede manipular directamente sistemas de archivos remotos.

!!! note
    Las operaciones de archivos requieren un dispositivo shell Conch configurado. Consulta [Shell Remoto](shell.md) para la configuración.

### Memoria

Almacenamiento de conocimiento persistente que abarca conversaciones:

- **Memoria Activa** — siempre incluida en cada llamada API. Úsala para hechos, preferencias o contexto que el modelo siempre debe recordar.
- **Memorias Guardadas** — una colección de archivos de memoria con nombre que el modelo puede buscar, leer, escribir y editar mediante llamadas a herramientas.

Consulta [Memoria y Caché](memory.md) para más detalles.

### Búsqueda de Conversaciones

El modelo puede buscar en tu historial de conversaciones pasadas usando métodos de palabra clave o semánticos (RAG). Esto le permite hacer referencia a discusiones previas sin que necesites encontrarlas y compartirlas manualmente.

Consulta [Búsqueda de Conversaciones](search.md) para la configuración.

---

## Interfaz de Herramientas en el Chat

Cuando se llama a una herramienta, la verás en línea en la conversación:

<div class="grid cards" markdown>

- **:material-progress-wrench: Banner de Llamada a Herramienta**

    ---

    Muestra el nombre de la herramienta y un estado breve (ej., :material-magnify: "Buscando 'últimas noticias de IA' en la web").

- **:material-check-circle: Resultado de Herramienta**

    ---

    Después de la ejecución, muestra el resultado formateado o resumen (ej., "Se encontraron 5 resultados para 'últimas noticias de IA'").

</div>

### Detalles Expandibles

Toca una llamada a herramienta para expandirla y ver:

- **Argumentos** — los parámetros exactos enviados a la herramienta
- **Resultado** — la salida bruta de la ejecución de la herramienta
- **Estado** — éxito, fallo o resultados parciales

### Llamadas Fallidas

Si una llamada a herramienta falla, el modelo es notificado del error y puede reintentar o ajustar. Verás un banner rojo con el mensaje de error.

---

## Permisos de Herramientas

Tú controlas a qué herramientas puede acceder el modelo:

| Configuración | Ubicación | Predeterminado |
|---------------|-----------|----------------|
| Búsqueda Web | Configuración → Búsqueda Web | Desactivado |
| Shell | Configuración → Shell | Desactivado |
| Memoria (Guardada) | Configuración → Memoria → Acceder a Memorias Guardadas | Desactivado |
| Memoria (Activa) | Configuración → Memoria → Acceder a Memoria Activa | Desactivado |
| Conversaciones Pasadas | Configuración → Memoria → Acceder a Conversaciones Pasadas | Desactivado |
| Búsqueda de Conversaciones | Configuración → Búsqueda de Conversaciones | Activado* |

*La capacidad del modelo para buscar conversaciones depende de tener un modelo de embedding configurado. Sin uno, solo está disponible la búsqueda por palabra clave.

---

## Bucles de Herramientas de Múltiples Rondas

El modelo puede encadenar múltiples llamadas a herramientas. Por ejemplo:

1. Usuario: "¿Cuál es la última versión del kernel de Linux y la está ejecutando mi servidor?"
2. El modelo llama a `web_search("última versión del kernel de Linux")`
3. El modelo llama a `shell_execute("uname -r", device="mi-servidor")`
4. El modelo compara los resultados y responde

Cada llamada a herramienta y su resultado aparecen como elementos separados en línea en la conversación antes de la respuesta de texto final.
