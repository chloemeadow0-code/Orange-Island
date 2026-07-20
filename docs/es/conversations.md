# Conversaciones

El sistema de conversaciones de 橘子岛 está construido alrededor de la **ramificación no lineal** — a diferencia de la mayoría de las aplicaciones de chat, puedes editar cualquier mensaje pasado y explorar rutas de respuesta alternativas sin perder la conversación original.

## Crear Conversaciones

Toca **Nuevo Chat** en el panel de conversaciones, o simplemente empieza a escribir en la pantalla de chat. Se crea una nueva conversación automáticamente con tu primer mensaje.

Las conversaciones reciben un título automático después de la primera respuesta (si la [Generación de Títulos](system-prompts.md#auto-title-generation) está habilitada), o puedes renombrarlas manualmente.

## Gestionar Conversaciones

### Cambiar de Conversación

Abre el **panel de conversaciones** (menú hamburguesa :material-menu: o desliza hacia la derecha) y toca cualquier conversación para abrirla.

### Renombrar

1. Mantén pulsada una conversación en el panel
2. Toca **Renombrar**
3. Introduce un nuevo título y guarda

### Eliminar

1. Mantén pulsada una conversación en el panel
2. Toca **Eliminar**
3. Confirma la eliminación — esta acción no se puede deshacer

---

## Ramificación No Lineal

Esta es la característica distintiva de 橘子岛. Cada mensaje puede ser un punto de ramificación.

### Editar un Mensaje Pasado

1. Mantén pulsada cualquier burbuja de mensaje (usuario o modelo)
2. Toca **Editar**
3. Modifica el contenido del mensaje
4. Envía — 橘子岛 crea una **nueva rama** desde este punto

La rama original se conserva. Puedes cambiar entre ramas en cualquier momento.

### Cómo Funcionan las Ramas

Cada mensaje vive en una **estructura de árbol**:

```text
Mensaje 1 (Usuario)
├── Mensaje 2 (Modelo) ← respuesta original
└── Mensaje 3 (Modelo) ← rama creada después de editar el Mensaje 1
    ├── Mensaje 4 (Usuario)
    └── ...
```

Cuando editas un mensaje y regeneras, la nueva respuesta se convierte en un hermano del original — ambos existen bajo el mismo mensaje padre.

### Cambiar de Rama

Cuando un mensaje tiene múltiples hijos (ramas), la interfaz muestra controles de navegación para cambiar entre ellas. Puedes explorar caminos alternativos sin perder el contexto.

### ¿Por Qué Ramificar?

- **Explorar alternativas** — haz la misma pregunta con diferente redacción
- **Probar prompts A/B** — compara respuestas de diferentes prompts del sistema o modelos
- **Corregir errores** — corrige un error tipográfico en tu pregunta sin perder el hilo original
- **Iterar** — refina un prompt a través de múltiples versiones manteniendo todos los intentos

---

## Operaciones de Mensajes

Mantén pulsado cualquier mensaje para acceder a estas acciones:

| Acción | Descripción |
|--------|-------------|
| **Copiar** | Copia el texto del mensaje al portapapeles |
| **Editar** | Edita el mensaje y crea una rama |
| **Información** | Ver metadatos: marca de tiempo, modelo usado, conteo de tokens |
| **Eliminar** | Elimina este mensaje y todas las respuestas siguientes |

!!! warning "Eliminar un Mensaje"
    Eliminar un mensaje también elimina todas las respuestas que lo siguen. Esto no se puede deshacer.

---

## La Barra Inferior

El área de entrada de chat proporciona acceso rápido a controles esenciales:

### Selector de Modelo

Toca el nombre del modelo en el lado izquierdo de la barra inferior para abrir el **selector de modelo**. Puedes cambiar de modelo en cualquier momento — incluso a mitad de una conversación. Diferentes mensajes en la misma conversación pueden usar diferentes modelos.

### Adjuntos

Toca **+** (:material-plus:) para adjuntar archivos:

- **Fotos** — imágenes de tu galería
- **Videos** — archivos de video (con soporte de extracción de fotogramas)
- **Archivos** — cualquier tipo de archivo, incluidos PDFs

Los formatos de imagen soportados se envían directamente a los modelos con capacidad de visión. Los archivos PDF abren un diálogo de selección de páginas.

### Enviar

Escribe tu mensaje y toca **Enviar** (:material-send:). El modelo transmite su respuesta token por token.

---

## Streaming y Visualización

### Streaming en Tiempo Real

Las respuestas aparecen palabra por palabra a medida que el modelo las genera. 橘子岛 se desplaza automáticamente para mantener visible el contenido más reciente. Toca el botón **ir al final** (aparece cuando te desplazas hacia arriba) para volver a la respuesta en vivo.

### Renderizado de Markdown

Las respuestas del modelo se renderizan con soporte completo de markdown:

- **Encabezados**, **negrita**, *cursiva*, `código en línea`
- **Bloques de código** con resaltado de sintaxis (usa ````` ``` `````)
- **Tablas**, citas, listas
- **Matemáticas LaTeX** — en línea `$E=mc^2$` y bloque `$$\int_a^b f(x)dx$$`

### Visualización del Razonamiento

Para modelos que soportan razonamiento (OpenAI serie-o, Anthropic extended thinking, Gemini thinking, DeepSeek-R1), el proceso de pensamiento del modelo se muestra en un **panel colapsable** antes de la respuesta final:

- El panel muestra "Pensando..." durante la fase de razonamiento
- Una vez completado, muestra la duración del razonamiento (ej., "Pensó durante 12s")
- Toca para expandir/colapsar el contenido del pensamiento
- Las llamadas a herramientas realizadas durante el razonamiento se cuentan (ej., "Pensó durante 8s, llamó a 2 herramientas")

---

## Configuración por Conversación

Cada conversación puede anular los valores predeterminados globales:

- **Modelo** — selecciona un modelo diferente para esta conversación
- **Prompt del Sistema** — usa una instrucción del sistema diferente
- **Parámetros de generación** — temperatura, tokens máximos, nivel de razonamiento

Estas anulaciones se establecen desde el menú de opciones de la conversación en la barra superior.

---

## Ventana de Contexto

橘子岛 rastrea el uso de tokens en tiempo real. Cuando una conversación excede la ventana de contexto del modelo, los mensajes más antiguos se **atenúan** visualmente para indicar que están fuera del contexto activo. El modelo ya no "ve" los mensajes atenuados, pero permanecen visibles en tu interfaz.

Ajusta el tamaño de la ventana de contexto en **Configuración → Generación → Ventana de Contexto**.
