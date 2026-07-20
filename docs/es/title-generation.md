# Generación de Títulos

Genera automáticamente títulos de conversación basados en el primer intercambio.

## Qué Hace

Cuando inicias una nueva conversación, 橘子岛 puede generar automáticamente un título corto y significativo basado en tu primer mensaje y la respuesta del modelo. Esto reemplaza el título genérico "Nuevo Chat".

## Configuración

1. Ve a **Configuración → Generación de Títulos**
2. Activa **Auto-generar títulos**
3. Opcionalmente elige un **Modelo** para la generación de títulos (usa el modelo de conversación actual por defecto)

!!! tip "Elección de Modelo"
    La generación de títulos usa muy pocos tokens. Puedes usar un modelo barato y rápido (como GPT-4o Mini o un modelo local) sin afectar la calidad de tu conversación.

## Cómo Funciona

1. Envías tu primer mensaje en una nueva conversación
2. El modelo responde (como de costumbre)
3. Después de que la respuesta se completa, 橘子岛 envía una solicitud separada y pequeña para generar un título
4. El título generado se guarda y se muestra en la lista de conversaciones

La generación de títulos solo se ejecuta una vez por conversación, en el primer intercambio.

## Modelo de Generación de Títulos

Puedes usar un modelo diferente específicamente para la generación de títulos:

- **Predeterminado** (sin selección) — Usa el mismo modelo que la conversación
- **Modelo específico** — Siempre usa ese modelo para toda la generación de títulos, independientemente de qué modelo se use para la conversación

Usar un modelo rápido dedicado para títulos puede reducir la latencia y el costo.
