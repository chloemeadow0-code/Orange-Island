# Parámetros de Generación

Controla cómo los modelos generan respuestas — desde la longitud del contexto hasta la configuración de creatividad.

## Ventana de Contexto

**Máximo de Mensajes de Contexto** establece cuántos mensajes recientes se envían al modelo como contexto. Predeterminado: **20**.

- **5–20** — Contexto más corto, respuestas más rápidas, menos consumo de tokens
- **20–50** — Contexto más largo para conversaciones complejas de múltiples turnos
- **50–100** — Contexto máximo para discusiones muy largas (puede alcanzar límites de tokens)

Esto se aplica a todos los modelos. La ventana de contexto real en tokens depende de tu modelo y la longitud de los mensajes.

---

## Temperatura

Controla la aleatoriedad en la salida del modelo. Rango: **0.0 – 2.0**.

- **0.0 – 0.3** — Más determinista, consistente, factual
- **0.5 – 0.8** — Creatividad equilibrada (predeterminado recomendado)
- **1.0 – 2.0** — Más aleatorio, creativo, impredecible

Una temperatura más alta significa que el modelo es más propenso a elegir palabras menos probables. Una temperatura más baja produce salidas más enfocadas y repetitivas.

!!! tip "Cuándo Ajustar"
    - **Código / Hechos**: Usa temperatura baja (0.0 – 0.3)
    - **Escritura Creativa**: Usa temperatura alta (0.8 – 1.2)
    - **Chat General**: Usa temperatura media (0.5 – 0.7)

---

## Top P (Muestreo de Núcleo)

Controla la diversidad de la selección de tokens. Rango: **0.0 – 1.0**.

El modelo considera solo el conjunto más pequeño de tokens cuya probabilidad acumulada excede `top_p`.

- **0.1** — Muy enfocado, solo los tokens más probables
- **0.5** — Diversidad moderada
- **0.9 – 1.0** — Diversidad completa (predeterminado recomendado)

Normalmente ajustas *o bien* la temperatura *o bien* top P — no ambos.

---

## Tokens Máximos Predeterminados

Establece un límite máximo de tokens para las respuestas del modelo. Cuando se establece, el modelo no generará más de esta cantidad de tokens en una sola respuesta. Cuando **no se establece** (predeterminado), se aplica el máximo propio del modelo.

Preajustes disponibles:

```
256   512   1024   2048
4096  8192  16384  32768
```

!!! tip "Dejar Sin Establecer para Flexibilidad"
    Para la mayoría de los casos de uso, deja esto sin establecer. Establece un límite solo cuando necesites longitudes de respuesta consistentes (ej., resúmenes cortos) o quieras limitar costos.

---

## Penalización de Frecuencia

Reduce la tendencia del modelo a repetir las mismas palabras. Rango: **-2.0 – 2.0**.

- **Valores positivos** (0.1 – 1.0) — Desalentar la repetición
- **Cero** (0.0) — Sin penalización (predeterminado)
- **Valores negativos** (-1.0 – -0.1) — Fomentar la repetición

---

## Penalización de Presencia

Anima al modelo a hablar sobre nuevos temas. Rango: **-2.0 – 2.0**.

- **Valores positivos** (0.1 – 1.0) — Fomentar la diversidad de temas
- **Cero** (0.0) — Sin penalización (predeterminado)
- **Valores negativos** — Mantenerse en el tema actual

---

## Razonamiento / Thinking

Habilita el razonamiento en cadena de pensamiento para modelos compatibles (ej., DeepSeek R1, Qwen3, Claude).

Cuando está habilitado, el modelo genera razonamiento interno antes de producir la respuesta final. Esto mejora la precisión para tareas complejas pero tarda más y usa más tokens.

### Nivel de Razonamiento

- **Bajo** — Razonamiento mínimo, más rápido
- **Medio** — Equilibrado (predeterminado)
- **Alto** — Razonamiento máximo para problemas complejos

!!! warning "No Todos los Modelos Soportan Razonamiento"
    El modo de razonamiento requiere un modelo que soporte tokens de razonamiento. Si tu modelo no lo soporta, esta configuración no tiene efecto.

---

## Visualizar Despliegue del Contexto

Cuando está habilitado, 橘子岛 indica visualmente qué mensajes están incluidos en la ventana de contexto actual vs. cuáles han sido desplazados (excluidos debido al límite de la ventana de contexto). Esto te ayuda a entender:

- Cuánto de tu conversación puede "ver" el modelo
- Cuándo los mensajes más antiguos salen del contexto
- Si necesitas aumentar la ventana de contexto

La visualización aparece como un marcador sutil en la vista de conversación.

---

## Cómo Funcionan los Parámetros

Todos los parámetros de generación son **anulables** — cuando no se establecen explícitamente, no se envían al modelo, y el modelo usa sus propios valores predeterminados. Cada parámetro tiene una opción de restablecer para volver el valor a "no establecido".

---

## Anulaciones por Conversación

Puedes anular los parámetros de generación para conversaciones individuales usando el diálogo de **Configuración Avanzada** en la pantalla de chat (mantén pulsado el botón de enviar o usa el menú ⋮).
