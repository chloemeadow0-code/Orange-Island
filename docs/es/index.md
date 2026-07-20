# Manual de Usuario de 橘子岛

Bienvenido al manual de usuario de 橘子岛. 橘子岛 es un cliente LLM BYOK (Bring Your Own Key) para Android con acceso a múltiples proveedores, conversaciones ramificadas no lineales, llamadas a herramientas agentivas, generación de imágenes y control remoto de dispositivos.

## Enlaces Rápidos

### Primeros Pasos

- **[Primeros Pasos](getting-started.md)** — instalar, configurar y enviar tu primer mensaje
- **[Preguntas Frecuentes](faq.md)** — respuestas a preguntas comunes

### Funcionalidades Principales

- **[Conversaciones](conversations.md)** — ramificación no lineal, operaciones de mensajes, streaming, renderizado de markdown
- **[Proveedores de API](provider.md)** — conectar con OpenAI, Anthropic, Google, DeepSeek, Ollama y endpoints personalizados
- **[Modelos](models.md)** — habilitar/deshabilitar modelos, alias, sincronización de modelos por proveedor
- **[Prompts del Sistema](system-prompts.md)** — editor de tres secciones, sustitución de variables, cambio por conversación
- **[Generación](generation.md)** — temperatura, top P, tokens máximos, razonamiento, penalizaciones de frecuencia/presencia
- **[Generación de Títulos](title-generation.md)** — generar automáticamente títulos de conversación
- **[Transcripción de Imágenes](transcription.md)** — pipeline de imagen a texto para modelos sin visión
- **[Generación de Imágenes](image-generation.md)** — generación de texto a imagen como herramienta de chat
- **[Apariencia](appearance.md)** — modo de tema, esquema de color, color dinámico, estilo de esquema, efectos de desenfoque

### Herramientas Agentivas

- **[Visión General](tools.md)** — cómo funciona la llamada a herramientas de múltiples rondas
- **[Búsqueda Web](web-search.md)** — integración con DuckDuckGo Lite, Brave, Serper, Tavily, SearXNG
- **[Shell Remoto (Conch)](shell.md)** — ejecución remota cifrada de comandos, operaciones de archivos, integración MCP
- **[Sandbox](sandbox.md)** — entorno local Alpine Linux para ejecución aislada de comandos

### Gestión del Conocimiento

- **[Búsqueda de Conversaciones](search.md)** — búsqueda por palabra clave y semántica (RAG) en el historial de chat
- **[Embedding / RAG](embedding.md)** — configurar modelos de embedding para recuperación semántica
- **[Memoria y Caché](memory.md)** — memoria activa, memorias guardadas, auto-caché

### Más

- **[Modelos Locales](local-model.md)** — ejecutar modelos GGUF en el dispositivo mediante llama.cpp
- **[Importación de PDF](pdf-import.md)** — extraer y enviar páginas PDF a modelos de visión
- **[Portabilidad de Datos](import-export.md)** — exportar/importar archivos .橘子岛, copia de seguridad automática, importar desde Claude y ChatGPT
- **[Idioma](language.md)** — cambiar entre inglés, chino, chino tradicional o el predeterminado del sistema
- **[Acerca de](about.md)** — información de versión, actualizaciones, opciones de documentación, enlaces, valoración

---

## Acerca de 橘子岛

橘子岛 es un cliente Android BYOK para usuarios avanzados de IA:

- **Sin intermediarios**: Conexiones directas a la API, sin telemetría, sin seguimiento
- **Almacenamiento en el dispositivo**: Todo reside localmente en una base de datos Room
- **Conversaciones no lineales**: Edita cualquier mensaje pasado y explora ramas alternativas
- **Agentivo por defecto**: Llamadas a herramientas de múltiples rondas con búsqueda web, generación de imágenes, ejecución de código, shell, operaciones de archivos y memoria
- **Control remoto**: Administra servidores a través del protocolo cifrado Conch
- **Código abierto**: Licencia MIT, [código fuente en GitHub](https://github.com/orangeisland/app)
