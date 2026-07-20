# Generación de Imágenes

Genera imágenes a partir de prompts de texto usando un modelo de texto a imagen, directamente dentro de tus conversaciones.

## Qué Hace

Cuando la generación de imágenes está habilitada, 橘子岛 puede convertir tus prompts en imágenes usando un modelo dedicado de texto a imagen (como DALL·E, GPT-Image, Imagen, FLUX, Stable Diffusion, Seedream, Qwen-Image y muchos otros). La imagen generada se devuelve a la conversación, para que puedas iterar sobre ella como cualquier otra respuesta.

La generación de imágenes usa su **propia selección de modelo**, independiente del modelo con el que chateas — así que puedes chatear con un modelo y generar imágenes con otro.

## Configuración

1. Ve a **Configuración → Generación de Imágenes**
2. Activa **Habilitar Generación de Imágenes**
3. Toca **Modelo** y elige un modelo de texto a imagen
4. Opcionalmente establece el **Tamaño Predeterminado** (ancho × alto)

!!! note "BYOK — credenciales dedicadas"
    La generación de imágenes usa su **propia clave API y URL base dedicadas**, independientes de tus proveedores de chat. Esto significa que puedes usar un servicio diferente para imágenes que para el chat. Ve a **Configuración → Generación de Imágenes** para configurar la clave, URL base, modelo y tamaño predeterminado.

## Selección de Modelo

Toca **Modelo** para elegir el modelo usado para la generación.

- El selector muestra modelos que parecen ser de texto a imagen, filtrados de todos tus modelos sincronizados, para que la lista sea corta.
- Si el modelo que quieres no aparece (un nombre inusual), habilita **Mostrar todos los modelos** para elegir de la lista completa.
- Solo una entrada `Proveedor:modelo` correctamente sincronizada cuenta como una selección válida. Sincroniza tus modelos primero en **Configuración → Proveedores de API** / **Gestionar Modelos** si la lista está vacía.

## Tamaño Predeterminado

Establece las dimensiones de salida predeterminadas, introducidas como **ancho × alto** en píxeles (por ejemplo `1024` × `1024`).

- El valor predeterminado es `1024 × 1024`.
- Los tamaños soportados dependen del modelo y del proveedor — si un modelo rechaza un tamaño, prueba un valor que documente (las opciones comunes son `1024×1024`, `1024×1792`, `1792×1024`).

## Cómo Funciona

1. Habilita la generación de imágenes y selecciona un modelo de imagen
2. En una conversación, pide al asistente que cree una imagen
3. 橘子岛 enruta la solicitud al modelo de imagen configurado usando las credenciales de ese proveedor
4. La imagen generada se devuelve a la conversación

!!! tip
    Sé específico en tu prompt — describe el sujeto, estilo, composición e iluminación. Los prompts claros producen resultados mucho mejores que los vagos.
