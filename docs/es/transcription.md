# Transcripción de Imágenes

Permite que un modelo de visión describa imágenes para que los modelos de solo texto puedan entenderlas.

## Qué Hace

Cuando envías una imagen a un modelo de solo texto, 橘子岛 puede usar un modelo de visión separado para generar primero una descripción de texto de la imagen. Esta descripción se incluye luego en el prompt enviado a tu modelo principal.

Esto te permite usar imágenes con cualquier modelo, incluso aquellos que no soportan visión de forma nativa.

## Configuración

1. Ve a **Configuración → Transcripción de Imágenes**
2. Elige un **Modelo de Transcripción** — debe ser un modelo con capacidad de visión (ej., GPT-4o, Gemini Flash, Qwen-VL)
3. Añade modelos a **Modelos Habilitados** — estos son los modelos de solo texto que recibirán descripciones de imágenes
4. Ajusta el **Tamaño de Lote** si envías muchas imágenes a la vez (cuántas imágenes describir por llamada API)

!!! tip "Modelos de Visión Locales"
    Puedes usar un modelo de visión local (con mmproj) como modelo de transcripción. Esto mantiene el procesamiento de imágenes en el dispositivo.

## Cómo Funciona

1. Adjuntas una imagen a tu mensaje
2. 橘子岛 detecta que tu modelo actual no soporta visión
3. La imagen se envía primero al modelo de transcripción
4. El modelo de transcripción genera una descripción de texto
5. Esta descripción se antepone al texto de tu mensaje
6. El texto combinado se envía a tu modelo principal

---

## Tamaño de Lote

Controla cuántas imágenes se describen por llamada API al modelo de transcripción.

- **1** — Describe una imagen a la vez (más llamadas API, más preciso)
- **5–10** — Describe múltiples imágenes por llamada (menos llamadas API, puede perder detalle)

El valor predeterminado depende del dispositivo. Valores más bajos dan mejores resultados pero cuestan más.

---

## Selección de Modelo

### Modelo de Transcripción

Este es el modelo de visión que genera descripciones de imágenes. Elige el modelo de visión más capaz disponible para ti.

### Modelos Habilitados

Estos son los modelos de solo texto que usarán la transcripción de imágenes. Solo los modelos en esta lista recibirán descripciones de imágenes transcritas. Otros modelos recibirán imágenes directamente (si las soportan) o no las recibirán en absoluto.
