# Prompts del Sistema

Los prompts del sistema definen la personalidad, el comportamiento y las reglas básicas del modelo. 橘子岛 te da un control detallado sobre cómo se ensamblan y envían las instrucciones al modelo.

## Editor de Tres Secciones

Cada plantilla de prompt del sistema tiene tres secciones editables independientemente:

```text
┌─────────────────────────────────┐
│ Prompt del Sistema              │ ← Instrucciones principales (personalidad, reglas, tono)
├─────────────────────────────────┤
│ Prefijo de Usuario              │ ← Antepuesto antes de cada mensaje del usuario
├─────────────────────────────────┤
│ Sufijo de Usuario               │ ← Añadido después de cada mensaje del usuario
└─────────────────────────────────┘
```

### Prompt del Sistema

El bloque de instrucciones principal. Aquí es donde defines:

- **Personalidad**: "Eres un desarrollador Python senior enfocado en arquitectura limpia."
- **Reglas**: "Responde siempre en chino. Usa viñetas para las listas."
- **Restricciones**: "Nunca te disculpes. Sé conciso. Prefiere código sobre explicación."

### Prefijo y Sufijo de Usuario

Estos envuelven cada mensaje que envías:

- **Prefijo de Usuario** — añadido antes del texto de tu mensaje. Útil para recordatorios o etiquetas de contexto.
- **Sufijo de Usuario** — añadido después del texto de tu mensaje. Útil para instrucciones de cierre.

**Ejemplo**: Si tu prefijo es `[Contexto: trabajando en documentos de 橘子岛]` y el sufijo es `\n\nPor favor responde en Markdown.`, el modelo recibe:

```text
[Contexto: trabajando en documentos de 橘子岛]
¿Cómo configuro la búsqueda web?
Por favor responde en Markdown.
```

---

## Crear un Prompt

1. Ve a **Configuración → Prompts del Sistema**
2. Toca **Añadir Nuevo Prompt**
3. Introduce un **título** (ej., "Traductor", "Revisor de Código", "Asistente en Chino")
4. Rellena las tres secciones:
    - Toca **Añadir Texto** para escribir contenido estático
    - Toca **Añadir Variable** para insertar valores dinámicos
5. Toca **Guardar**

### Reordenar Elementos

Dentro de cada sección, puedes tener múltiples bloques de texto y variables. Mantén pulsado un elemento para:

- **Subir** / **Bajar** — reordenar dentro de la sección
- **Eliminar** — borrar el elemento

---

## Sustitución de Variables

Las variables se reemplazan con valores dinámicos cuando se envía el mensaje:

| Variable | Se expande a | Ejemplo | Cuándo se Resuelve |
|----------|-------------|---------|---------------------|
| `{time}` | Hora actual (HH:mm:ss) | `14:30:00` | Compilación del prompt |
| `{date}` | Fecha actual (YYYY-MM-DD) | `2026-05-10` | Compilación del prompt |
| `{sent_time}` | Hora de envío del mensaje (HH:mm) | `10:05` | Por mensaje |
| `{sent_date}` | Fecha de envío del mensaje (YYYY-MM-DD) | `2026-05-11` | Por mensaje |
| `{active_memory}` | Contenido de la memoria activa | `[Tu contenido de memoria guardado]` | Compilación del prompt |
| `{model_id}` | ID del modelo actualmente seleccionado | `gemini-1.5-flash` | Compilación del prompt |

**Variables por mensaje** (`{sent_time}`, `{sent_date}`) se resuelven cada vez que envías un mensaje, por lo que reflejan la hora exacta de envío. **Variables a nivel de prompt** (`{time}`, `{date}`, `{active_memory}`, `{model_id}`) se resuelven cuando se compila el prompt del sistema.

!!! tip
    Usa `{sent_date}` para prompts sensibles a la fecha como "Hoy es {sent_date}. Al discutir eventos recientes, ten en cuenta que tu conocimiento puede estar desactualizado." Usa `{active_memory}` para inyectar la memoria persistente del modelo en las instrucciones del sistema.

### Añadir una Variable

1. En cualquier sección del editor, toca **Añadir Variable**
2. Selecciona la variable del selector
3. Aparece como una píldora/chip en la sección — arrastra para reposicionar

---

## Gestionar Prompts

### Establecer como Predeterminado

Toca el botón de radio junto a un prompt para convertirlo en el **predeterminado global**. Todas las conversaciones usan este prompt a menos que se anule.

### Anulación por Conversación

Cada conversación puede usar un prompt del sistema diferente:

1. Abre una conversación
2. Toca el menú de opciones (:material-dots-vertical:) en la barra superior
3. Selecciona **Prompt de Conversación**
4. Elige un prompt de la lista

La configuración por conversación anula el predeterminado global solo para esa conversación.

### Editar o Eliminar

- Toca un prompt para **editarlo**
- Mantén pulsado y selecciona **Eliminar** para quitarlo

!!! warning
    Eliminar un prompt del sistema es permanente. Las conversaciones que lo usaban volverán al predeterminado global.

---

## Sin Prompt del Sistema

Si no se selecciona ningún prompt del sistema, el modelo no recibe instrucciones especiales — se comporta según su entrenamiento base. Esto a veces es deseable para pruebas o para modelos que funcionan mejor sin instrucciones del sistema.

Para no usar ningún prompt, selecciona **Ninguno** de la lista de prompts.

---

## Generación Automática de Títulos

橘子岛 puede generar automáticamente títulos de conversación después de la primera respuesta:

1. Ve a **Configuración → Generación de Títulos**
2. Habilita **Auto-Generar Título**
3. Elige un **Modelo de Título**:
    - **Usar Modelo Actual** — usa el modelo que esté activo en la conversación
    - **Seleccionar Modelo de Título** — elige un modelo rápido/barato específico para generar títulos

Cuando está habilitado, aparece un breve snackbar "Generando título..." después de la primera respuesta del modelo, y la conversación se renombra automáticamente de "Sin título" a un título descriptivo.

---

## Ejemplos de Prompts

### Traductor

```yaml
Prompt del Sistema: |
  Eres un traductor profesional. Traduce la entrada del usuario al inglés.
  Conserva el formato, los bloques de código y los términos técnicos. No añadas explicaciones.
```

### Revisor de Código

```yaml
Prompt del Sistema: |
  Eres un revisor de código senior. Cuando se te muestre código:
  1. Identifica errores y casos límite
  2. Sugiere mejoras de rendimiento
  3. Verifica problemas de seguridad
  Sé específico. Haz referencia a los números de línea cuando sea posible.
```

### Asistente en Chino

```yaml
Prompt del Sistema: |
  你是一个乐于助人的中文助手。用简洁、清晰的中文回答问题。
Sufijo de Usuario: |
  \n\n请用中文回答。
```
