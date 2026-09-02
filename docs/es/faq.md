# Preguntas Frecuentes

## API y Proveedores

### ¿Cómo obtengo una clave API?

- **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — nivel gratuito disponible
- **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
- **Anthropic**: [Console API Keys](https://console.anthropic.com/)
- **DeepSeek**: [Platform](https://platform.deepseek.com/)
- **OpenRouter**: [Página de claves](https://openrouter.ai/keys)
- **Brave Search**: [Brave Search API](https://api.search.brave.com/)

### ¿Puedo usar múltiples claves API para el mismo proveedor?

Sí. Cada proveedor admite múltiples claves con nombre. Toca el botón de radio para seleccionar la clave activa. Útil para rotar entre claves de trabajo/personales o tener una copia de seguridad lista. Consulta [Proveedores de API](provider.md#api-keys).

### ¿Cómo añado un proveedor personalizado?

Ve a Configuración → Proveedor → **+ Añadir Proveedor Personalizado**. Introduce un nombre y una URL base. Cualquier endpoint compatible con OpenAI funciona. Consulta [Proveedores Personalizados](provider.md#custom-providers).

---

## Modelos Locales

### ¿Qué modelos GGUF funcionan?

橘子岛 soporta el formato GGUF tanto para chat como para embedding. Los modelos de chat deben caber en la memoria del dispositivo (1–8B parámetros dependiendo de la RAM). Los modelos de embedding son mucho más pequeños (100–500 MB). Consulta [Modelos Locales](local-model.md).

### ¿Cómo ejecuto modelos sin conexión?

Importa un modelo de chat GGUF a través de Configuración → Proveedor → Local → **Importar Modelo GGUF**. Para búsqueda semántica completamente offline, importa también un modelo de embedding GGUF. No se necesita conexión de red.

### ¿Por qué mi modelo local es tan lento?

La inferencia local se ejecuta en la CPU de tu dispositivo. Es inherentemente más lenta que las APIs en la nube. Consejos: usa modelos más pequeños (1–3B parámetros), cuantización más baja (Q4_K_M), ventanas de contexto más cortas y cierra aplicaciones en segundo plano.

---

## Embeddings y Búsqueda

### ¿Por qué falla la prueba de mi modelo de embedding?

Causas comunes:

- **Nombre de modelo incorrecto** — verifica la ortografía exacta, incluyendo las etiquetas de Ollama (ej., `qwen3-embedding:8b` no `qwen3-embedding`)
- **URL base incorrecta** — asegúrate de que el endpoint soporte `/v1/embeddings`
- **Falta de clave API** — algunos proveedores requieren autenticación incluso para embeddings
- **Red** — verifica la conectividad con el endpoint

### ¿Cuál es la diferencia entre búsqueda por palabra clave y RAG?

La búsqueda por palabra clave coincide con texto exacto. RAG (búsqueda semántica) coincide por significado — "configuración de base de datos" puede encontrar "configuración de Room" incluso sin palabras compartidas. RAG requiere un modelo de embedding y mensajes en caché. Consulta [Búsqueda de Conversaciones](search.md).

### ¿Cómo uso Ollama para embeddings?

1. Instala Ollama en una máquina
2. Descarga un modelo de embedding: `ollama pull qwen3-embedding:8b`
3. En 橘子岛, añade un modelo de embedding remoto con el preset **Ollama**
4. Usa `http://<host>:11434/v1` como URL base
5. Introduce el nombre exacto del modelo incluyendo la etiqueta (ej., `qwen3-embedding:8b`)
6. Deja la clave API en blanco

---

## Memoria

### ¿Cuál es la diferencia entre Memoria Activa y Memorias Guardadas?

**Memoria Activa** es un único contexto persistente incluido con cada llamada API — el modelo siempre lo ve. **Memorias Guardadas** son una colección de archivos con nombre que el modelo busca y recupera bajo demanda. Usa Memoria Activa para hechos persistentes; usa Memorias Guardadas para material de referencia. Consulta [Memoria y Caché](memory.md).

### ¿Puede el modelo modificar mis memorias?

Sí, si habilitas **Acceder a Memorias Guardadas** y/o **Acceder a Memoria Activa** en Configuración → Memoria. El modelo puede crear, leer, editar y eliminar memorias mediante llamadas a herramientas. Todos los permisos están desactivados por defecto.

---

## Shell y Herramientas

### ¿Cómo configuro el acceso a shell remoto?

Despliega el servidor [Conch](https://github.com/newo-ether/conch) en tu máquina de destino, luego añade el dispositivo en Configuración → Shell con su URL y clave API. Consulta [Shell Remoto](shell.md).

### ¿Puedo buscar en la web sin una clave API?

Sí. **DuckDuckGo Lite** es el proveedor de búsqueda web predeterminado y no requiere clave API. Funciona de inmediato — solo habilita Búsqueda Web en Configuración → Búsqueda Web. Para mayor fiabilidad, configura uno de los proveedores basados en API (Brave, Serper, Tavily, SearXNG). Consulta [Búsqueda Web](web-search.md).

### ¿Está cifrada la conexión del shell?

Sí. Conch utiliza intercambio de claves ECDH + cifrado AES-256-GCM + firma HMAC-SHA256. Todo el tráfico entre 橘子岛 y el servidor Conch está cifrado de extremo a extremo.

---

## Datos

### ¿Cómo hago una copia de seguridad de mis datos?

Ve a Configuración → Control de Datos → **Exportar Datos** para crear una copia de seguridad manual en un archivo `.橘子岛`. Para protección sin intervención, activa **Copia de Seguridad Automática** en Configuración → Control de Datos → Copia de Seguridad Automática — realiza copias de seguridad periódicas en segundo plano. Consulta [Portabilidad de Datos](import-export.md).

### ¿Puedo importar desde ChatGPT o Claude?

Sí. Exporta tus datos desde ChatGPT o Claude (proporcionan archivos `.zip`), luego importa en Configuración → Control de Datos → **Terceros**. Se admiten las estrategias Fusionar y Reemplazar. Consulta [Portabilidad de Datos](import-export.md#third-party-import).

### ¿Se incluyen mis claves API en las exportaciones?

Pueden incluirse, pero es opcional. La pantalla de exportación te permite activar la inclusión de claves API. Se muestra una advertencia cuando la activas. Las claves se almacenan en texto plano dentro del archivo `.橘子岛`, así que solo inclúyelas para migraciones completas de dispositivo a destinos de confianza.

---

## General

### ¿Dónde se almacenan mis datos?

Todo se almacena localmente en tu dispositivo Android en una base de datos Room. 橘子岛 no tiene servidores, ni sincronización en la nube, ni telemetría. Los mensajes se envían directamente desde tu dispositivo al proveedor de IA que configures.

### ¿橘子岛 admite varios idiomas?

Sí. La interfaz de la aplicación admite **English**, **中文 (Chino)** y **繁體中文 (Chino Tradicional)**. Configuración → Idioma. Se requiere reiniciar después de cambiar.

### ¿Cómo reporto un error o solicito una función?

Abre un issue en [GitHub](https://github.com/chloemeadow0-code/Orange-Island/issues). Para contribuciones, consulta la sección [Contributing](https://github.com/chloemeadow0-code/Orange-Island#contributing) del README.
