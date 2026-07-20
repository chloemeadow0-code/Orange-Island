# Embedding / RAG

Los modelos de embedding convierten texto en vectores numéricos que capturan el significado. 橘子岛 usa estos vectores para búsqueda semántica (RAG) en tu historial de conversaciones — encontrando mensajes por lo que significan, no solo por las palabras que contienen.

## Cómo Funciona

1. Cada mensaje se envía a un modelo de embedding
2. El modelo devuelve un vector (una lista de números) que representa el significado del mensaje
3. Cuando buscas, tu consulta también se incrusta
4. 橘子岛 calcula la **similitud coseno** entre el vector de consulta y todos los vectores de mensajes
5. Los mensajes con similitud por encima de tu umbral se devuelven como coincidencias

## Proveedores Soportados

| Proveedor | URL Base | Requiere Clave API | Notas |
|-----------|----------|--------------------|-------|
| **OpenAI** | `https://api.openai.com/v1` | Sí | `text-embedding-3-small`, `text-embedding-3-large` |
| **Mistral** | `https://api.mistral.ai/v1` | Sí | `mistral-embed` |
| **Voyage AI** | `https://api.voyageai.com/v1` | Sí | `voyage-3`, `voyage-3-lite` |
| **SiliconFlow** | `https://api.siliconflow.cn/v1` | Sí | `BAAI/bge-large-zh-v1.5` (optimizado para chino) |
| **Ollama** | `http://localhost:11434/v1` | No | `qwen3-embedding`, `nomic-embed-text`, etc. |
| **Personalizado** | Cualquiera | Opcional | Cualquier endpoint de embeddings compatible con OpenAI |
| **Local** | N/A | No | Modelos de embedding GGUF mediante llama.cpp |

---

## Añadir un Modelo de Embedding

### Remoto (API)

1. Ve a **Configuración → Búsqueda de Conversaciones**
2. Toca **Añadir Modelo Remoto**
3. Configura:

| Campo | Descripción |
|-------|-------------|
| **Proveedor** | Selecciona del desplegable (OpenAI, Mistral, Voyage, SiliconFlow, Ollama, Personalizado) |
| **Nombre del Modelo** | El ID exacto del modelo (ej., `text-embedding-3-small`) |
| **URL Base** | Se rellena automáticamente para proveedores conocidos; editable para proxies |
| **Clave API** | Déjala en blanco para resolver automáticamente desde tu clave de proveedor de chat, o introduce una clave dedicada |
| **Tamaño de Lote** | Mensajes a incrustar por solicitud API (1–100) |

4. Toca **Añadir** — se ejecuta una prueba de conexión antes de guardar

!!! tip
    El campo de clave API es opcional si ya has configurado el mismo proveedor para el chat. Déjalo en blanco y 橘子岛 resuelve tu clave API de chat automáticamente.

### Local (GGUF)

1. Ve a **Configuración → Búsqueda de Conversaciones**
2. Toca **Añadir Modelo Local**
3. Importa un archivo de modelo de embedding `.gguf` (ej., `bge-small-en-v1.5-q4_k.gguf`)
4. Dale un nombre
5. Toca **Añadir**

Los modelos de embedding son típicamente mucho más pequeños que los modelos de chat — unos pocos cientos de MB como máximo.

### Ollama

1. Instala Ollama en una máquina
2. Descarga un modelo de embedding: `ollama pull qwen3-embedding:8b`
3. En 橘子岛, añade un modelo remoto:
    - Proveedor: **Ollama**
    - URL Base: `http://<host>:11434/v1`
    - Nombre del modelo: `qwen3-embedding:8b` (incluye la `:etiqueta`)
    - Clave API: dejar en blanco
4. Toca **Añadir**

!!! note
    Las etiquetas de sufijo de Ollama como `:8b`, `:latest` son parte del nombre del modelo. Usa el nombre exacto de `ollama list`.

---

## Caché

Después de añadir un modelo, necesitas cachear tus mensajes (generar embeddings):

1. Toca **Cachear** en el modelo de embedding
2. 橘子岛 procesa todos los mensajes no cacheados en lotes
3. Un indicador de progreso circular muestra el progreso actual
4. Finalización: "Todos los N mensajes cacheados"

### Auto-Caché

Habilita **Auto-caché** para incrustar automáticamente los nuevos mensajes a medida que llegan. Esto mantiene tu índice de búsqueda siempre actualizado.

### Re-Cachear

Toca **Re-cachear** para eliminar todos los embeddings existentes y reconstruir desde cero. Úsalo cuando:

- Cambias a un modelo de embedding diferente
- La calidad del embedding parece degradada
- El caché es inconsistente

!!! warning
    Re-cachear no se puede deshacer y puede tardar mucho tiempo para historiales de mensajes grandes.

---

## Tamaño de Lote

La configuración de **Tamaño de Lote** (1–100) controla cuántos mensajes se envían por solicitud API durante el cacheo:

- **Más alto**: Cacheo más rápido, pero cargas API más grandes
- **Más bajo**: Solicitudes más pequeñas, más lento pero más fiable en conexiones lentas

Comienza con el valor predeterminado y ajusta si encuentras tiempos de espera (bájalo) o quieres un cacheo más rápido (súbelo).

---

## Probar Tu Configuración

Cuando añades un modelo remoto, 橘子岛 ejecuta una prueba de conexión automática. Si falla:

1. Verifica el nombre del modelo — incluye etiquetas para Ollama (`:8b`, `:latest`)
2. Comprueba que la URL base sea accesible desde tu dispositivo
3. Confirma que la clave API sea válida (si se requiere)
4. Prueba con un nombre de modelo conocido para ese proveedor

Errores comunes:
- **"Nombre de modelo incorrecto"** — verifica la ortografía exacta, incluyendo etiquetas
- **"URL base incorrecta"** — asegúrate de que el endpoint soporte `/v1/embeddings`
- **"Falta clave API"** — algunos proveedores requieren autenticación
- **"Error de red"** — verifica la conectividad

---

## Recomendaciones de Proveedores

| Caso de Uso | Proveedor Recomendado |
|-------------|----------------------|
| **Mejor calidad (inglés)** | Voyage AI `voyage-3` |
| **Mejor calidad (chino)** | SiliconFlow `BAAI/bge-large-zh-v1.5` |
| **Gratuito / autoalojado** | Ollama `qwen3-embedding` o `nomic-embed-text` |
| **Completamente offline** | GGUF local `bge-small-en-v1.5` |
| **Ya usas OpenAI** | OpenAI `text-embedding-3-small` (barato, rápido) |
