# Búsqueda de Conversaciones

橘子岛 puede buscar en todo tu historial de conversaciones — ya sea por coincidencia de palabras clave o por recuperación semántica (basada en significado) usando modelos de embedding.

## Métodos de Búsqueda

### Búsqueda por Palabra Clave

Coincidencia de texto rápida y exacta. La búsqueda encuentra ocurrencias literales de tu consulta en el contenido de los mensajes.

**Ideal para:**
- Encontrar una frase o término específico
- Búsquedas rápidas cuando recuerdas la redacción exacta
- Sin configuración — funciona inmediatamente

**Limitaciones:**
- No detecta sinónimos ni conceptos relacionados
- Sin comprensión del significado

### Búsqueda Semántica (RAG)

Utiliza modelos de embedding para encontrar mensajes por **significado**, no por palabras exactas. Una consulta como "cómo configurar la base de datos" puede encontrar mensajes sobre "configuración de Room" incluso si la palabra "base de datos" nunca aparece.

**Ideal para:**
- Encontrar conversaciones por tema o asunto
- Consultas amplias donde no recuerdas la redacción exacta
- Descubrir discusiones relacionadas en diferentes conversaciones

**Requisitos:**
- Se debe configurar un modelo de embedding (consulta [Embedding / RAG](embedding.md))
- Los mensajes deben estar cacheados (embeddings generados)

---

## Configuración

### 1. Añadir un Modelo de Embedding

Consulta [Embedding / RAG](embedding.md) para la configuración detallada. Puedes usar:
- **Modelos remotos** (OpenAI, Mistral, Voyage, Ollama, etc.)
- **Modelos locales** (archivos GGUF, completamente offline)

### 2. Elegir Métodos de Búsqueda

En **Configuración → Búsqueda de Conversaciones**:

| Configuración | Descripción |
|---------------|-------------|
| **Método de Búsqueda del Modelo** | Cómo busca el modelo cuando llama a la herramienta `search_conversations` |
| **Método de Búsqueda Manual** | Cómo funciona la barra de búsqueda en el panel de conversaciones |

Establece cada uno en **Palabra Clave** o **Semántica (RAG)**.

### 3. Configurar el Alcance de Búsqueda

| Configuración | Rango | Descripción |
|---------------|-------|-------------|
| **Mensajes de contexto por resultado** | 4–32 | Cuántos mensajes circundantes incluir con cada coincidencia (pasos: 4, 8, 12, 16, 20, 24, 28, 32) |
| **Máximo de resultados** | 5–30 | Número máximo de coincidencias a devolver (pasos: 5, 10, 15, 20, 25, 30) |
| **Umbral de Similitud** | 0.0–1.0 | Solo RAG: puntuación mínima de similitud para una coincidencia. Más alto = más estricto. Predeterminado: 0.5 |

### 4. Cachear Mensajes

Si usas RAG, toca **Cachear** para generar embeddings para todos los mensajes existentes. Habilita **Auto-caché** para mantener el índice actualizado automáticamente.

---

## Usar la Búsqueda

### Búsqueda Manual (Barra de Búsqueda)

1. Abre el **panel de conversaciones** (menú hamburguesa :material-menu: o desliza hacia la derecha)
2. Toca la barra de búsqueda en la parte superior
3. Escribe tu consulta
4. Los resultados aparecen debajo — toca cualquier resultado para abrir esa conversación en el mensaje coincidente

### Búsqueda Iniciada por el Modelo

Cuando **Acceder a Conversaciones Pasadas** está habilitado (Configuración → Memoria), el modelo puede buscar en tu historial de forma autónoma:

```text
Tú: "¿Qué decidimos sobre el diseño de la API la semana pasada?"
Modelo: [Busca "decisión de diseño de API"]
        "El martes pasado, decidimos usar..."
```

La búsqueda aparece como una tarjeta de herramienta en la conversación.

---

## Umbral de Similitud

El deslizador de **Umbral de Similitud** (0.0 a 1.0) controla cuán estrechamente debe coincidir un mensaje para ser incluido en los resultados de RAG:

- **Bajo (0.3–0.5)**: Más resultados, puede incluir contenido vagamente relacionado
- **Medio (0.5–0.7)**: Equilibrado — buen valor predeterminado
- **Alto (0.7–0.9)**: Menos resultados, solo coincidencias muy cercanas

Comienza con el valor predeterminado y ajusta según tus resultados. Si obtienes demasiadas coincidencias irrelevantes, sube el umbral. Si te faltan conversaciones relevantes, bájalo.

---

## Visualización de Resultados de Búsqueda

En el panel de conversaciones, los resultados de búsqueda muestran:

- **Título de la conversación** (o "Sin título")
- **Mensaje coincidente** — el mensaje de usuario o modelo que coincidió
- **Etiqueta de rol** — Usuario o Modelo
- **Mensajes de contexto** — mensajes circundantes para contexto

Toca un resultado para abrir la conversación desplazada hasta el mensaje coincidente.
