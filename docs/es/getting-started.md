# Primeros Pasos

Esta guía te explica cómo instalar 橘子岛, añadir tu primera clave API y enviar tu primer mensaje.

## Instalación

### Desde F-Droid (Recomendado)

橘子岛 está disponible en F-Droid, la tienda de aplicaciones de código abierto para Android.

1. Instala [F-Droid](https://f-droid.org/) en tu dispositivo
2. Abre F-Droid, busca **橘子岛**
3. Toca **Instalar**

### Desde GitHub Releases

1. Visita la [página de Releases](https://github.com/chloemeadow0-code/Orange-Island/releases)
2. Descarga el archivo `.apk` más reciente
3. Abre el archivo en tu dispositivo y confirma la instalación cuando se solicite

### Compilar desde el Código Fuente

Si prefieres compilarlo tú mismo:

1. Clona el repositorio:
   ```
   git clone https://github.com/chloemeadow0-code/Orange-Island.git
   ```
2. Abre el proyecto en [Android Studio](https://developer.android.com/studio) (Ladybug o más reciente)
3. Sincroniza Gradle y compila

Requisitos: Android SDK 34+, JDK 17+.

---

## Primer Inicio

Cuando abras 橘子岛 por primera vez, verás una pantalla de bienvenida con un campo de texto. Antes de poder chatear, necesitas configurar un proveedor y una clave API.

### Paso 1: Añadir una Clave API

1. Toca el icono de **Configuración** (engranaje inferior derecho) en la barra de navegación
2. En **Servicios**, toca **Proveedor**
3. Selecciona un proveedor de la lista (ej., **OpenAI**, **Anthropic**, **Google**)
4. Toca **Añadir Nueva Clave**
5. Introduce un nombre para tu clave (ej., "Personal") y pega tu clave API
6. Toca **Añadir**

??? tip "¿Dónde consigo una clave API?"
    - **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — nivel gratuito disponible
    - **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic**: [Console API Keys](https://console.anthropic.com/)
    - **DeepSeek**: [Platform](https://platform.deepseek.com/)
    - **OpenRouter**: [Página de claves](https://openrouter.ai/keys)

    Consulta la página de [Proveedores de API](provider.md) para detalles sobre cada proveedor.

### Paso 2: Sincronizar Modelos

1. Vuelve a Configuración y toca **Modelos** (en **Servicios**)
2. Toca **Sincronizar de Todos los Proveedores**
3. 橘子岛 obtiene la lista de modelos más reciente de todos los proveedores configurados
4. Una vez sincronizado, toca un modelo para establecerlo como tu **Modelo Predeterminado**

### Paso 3: Enviar Tu Primer Mensaje

1. Toca la **flecha hacia atrás** para volver a la pantalla de chat
2. Escribe un mensaje en el campo de entrada en la parte inferior
3. Toca **Enviar** (icono de avión de papel)

El modelo transmitirá su respuesta en tiempo real.

---

## Diseño de la Aplicación

橘子岛 tiene un diseño limpio centrado en la pantalla de chat:

### Barra Superior

- **Título de la conversación** — muestra el nombre de la conversación actual (toca para renombrar)
- **Menú hamburguesa** (:material-menu:) — abre el panel de conversaciones
- **Menú de opciones** (:material-dots-vertical:) — configuración por conversación (modelo, prompt del sistema, parámetros de generación)

### Panel de Conversaciones

Toca el **menú hamburguesa** o desliza hacia la derecha desde el borde izquierdo para abrir:

- **Barra de búsqueda** — encuentra conversaciones pasadas por palabra clave o búsqueda semántica
- **Lista de conversaciones** — todas las conversaciones, las más recientes primero
- **Configuración** (:material-cog:) — configura proveedores, modelos, prompts y más
- **Nuevo Chat** — inicia una conversación nueva

### Pantalla de Chat

- **Área de mensajes** — historial de conversación desplazable con renderizado de markdown
- **Barra inferior** — entrada de texto, selector de modelo, botón de adjuntos (+) y botón de enviar

---

## Próximos Pasos

- [Configurar prompts del sistema](system-prompts.md) para personalizar el comportamiento del modelo
- [Configurar búsqueda web](web-search.md) para acceso a internet en vivo
- [Explorar herramientas agentivas](tools.md) — ejecución de shell, operaciones de archivos y memoria
- [Importar datos](import-export.md) desde Claude o ChatGPT
- [Ejecutar modelos locales](local-model.md) para uso sin conexión
