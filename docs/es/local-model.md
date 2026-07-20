# Modelos Locales

Ejecuta LLMs directamente en tu dispositivo Android usando archivos de modelo GGUF y llama.cpp. Sin necesidad de red, sin claves API, completamente privado.

## Cómo Funciona

橘子岛 incluye llama.cpp a través de Android NDK (CMake). Cuando importas un archivo GGUF, el modelo se ejecuta completamente en la CPU de tu dispositivo — ningún dato sale del dispositivo.

## Requisitos

- Solo modelos en formato **GGUF** (el estándar para llama.cpp)
- **Memoria del dispositivo**: El modelo debe caber en la RAM disponible. Como regla general:
    - Modelos de 1–3B parámetros: 4–6 GB RAM
    - Modelos de 7–8B parámetros: 6–8 GB RAM
- **Almacenamiento**: Los archivos GGUF varían desde ~500 MB (modelos pequeños cuantizados) hasta más de 5 GB

!!! warning
    La inferencia local es intensiva en CPU y mucho más lenta que las APIs en la nube. Es mejor para uso sin conexión, contenido sensible a la privacidad o experimentación — no para chat rápido de alto volumen.

---

## Importar un Modelo de Chat

1. Descarga un archivo de modelo GGUF en tu dispositivo (consulta las fuentes recomendadas más abajo)
2. Ve a **Configuración → Proveedor**
3. Selecciona **Local** como proveedor
4. Toca **Importar Modelo GGUF**
5. Selecciona el archivo `.gguf` de tu dispositivo
6. Configura el modelo:

| Parámetro | Descripción | Ejemplo |
|-----------|-------------|---------|
| **ID del Modelo** | Identificador en minúsculas, sin espacios | `qwen3-8b` |
| **Alias** | Nombre descriptivo | `Qwen 3 8B` |
| **Tamaño de Contexto** | Ventana de contexto máxima en tokens | `4096` |
| **Temperatura** | Aleatoriedad (0.0–2.0) | `0.7` |
| **Top P** | Umbral de muestreo de núcleo (0.0–1.0) | `0.9` |
| **Tokens Máximos** | Longitud máxima de generación | `2048` |

7. Toca **Añadir**

El modelo se importa y está listo para usar inmediatamente.

---

## Importar un Modelo de Embedding

Los modelos de embedding son más pequeños y se usan para búsqueda semántica:

1. Ve a **Configuración → Búsqueda de Conversaciones**
2. Toca **Añadir Modelo Local**
3. Selecciona un archivo de modelo de embedding `.gguf`
4. Dale un nombre
5. Toca **Añadir**

Consulta [Embedding / RAG](embedding.md) para la configuración de búsqueda.

---

## Seleccionar el Modelo Activo

Después de importar uno o más modelos:

1. Ve a **Configuración → Proveedor → Local**
2. Verás todos los modelos importados listados
3. Toca el **botón de radio** junto al modelo que quieres usar
4. El modelo seleccionado se vuelve activo cuando se elige **Local** como proveedor de chat

---

## Gestionar Modelos Locales

### Renombrar

Toca un modelo para cambiar su alias o ajustar parámetros (temperatura, tamaño de contexto, etc.).

### Eliminar

Mantén pulsado un modelo y toca **Eliminar**. Esto elimina el modelo de 橘子岛 y borra el archivo GGUF del almacenamiento.

---

## Modelos Recomendados

### Modelos de Chat

| Modelo | Tamaño | RAM Necesaria | Notas |
|--------|--------|---------------|-------|
| Qwen 3 1.7B | ~1 GB | 3–4 GB | Buena calidad para su tamaño |
| Llama 3.2 3B | ~2 GB | 4–5 GB | Sólido en todos los aspectos |
| Qwen 3 8B | ~5 GB | 7–8 GB | Mejor calidad, RAM alta |

### Modelos de Embedding

| Modelo | Tamaño | Notas |
|--------|--------|-------|
| BGE Small EN v1.5 | ~130 MB | Buenos embeddings en inglés |
| BGE Small ZH v1.5 | ~130 MB | Optimizado para chino |
| Nomic Embed Text v1.5 | ~270 MB | Bueno multilingüe |

### Dónde Obtener Archivos GGUF

- [Hugging Face](https://huggingface.co/models?library=gguf) — busca "GGUF"
- [Modelos cuantizados de bartowski](https://huggingface.co/bartowski) — amplia selección, bien organizada

!!! tip
    Busca la cuantización `Q4_K_M` — ofrece el mejor equilibrio entre calidad y tamaño para modelos de chat.

---

## Consejos de Rendimiento

- **Contexto más pequeño = más rápido**: Comienza con 2048 y aumenta solo si es necesario
- **Cuantización más baja = más rápido**: Q4_K_M es más rápido que Q6_K o Q8
- **Cierra otras aplicaciones**: La inferencia local necesita tanta RAM como sea posible
- **Conéctate a la corriente**: La inferencia es intensiva en CPU, y el uso prolongado agota la batería
