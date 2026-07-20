# Proveedores de API

橘子岛 se conecta directamente a proveedores de IA — sin intermediarios, sin suscripción, sin telemetría. Tú traes tus propias claves API y todo se ejecuta desde tu dispositivo.

## Proveedores Integrados

| Proveedor | URL Base | Modelos | Notas |
|-----------|----------|---------|-------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Serie Gemini | Nivel gratuito disponible a través de Google AI Studio |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4, GPT-4o, serie-o | Modelos de razonamiento soportados |
| **Anthropic** | `https://api.anthropic.com/v1` | Serie Claude | Razonamiento extendido soportado |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3, DeepSeek-R1 | Modelos de razonamiento soportados |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Serie Qwen | A través de Alibaba DashScope |
| **Ollama** | `http://localhost:11434/v1` | Cualquier modelo descargado | Autoalojado, no requiere clave API |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Múltiples proveedores | Accede a muchos modelos a través de una sola API |
| **Local** | N/A | Modelos GGUF | En el dispositivo mediante llama.cpp, completamente offline |

## Cambiar de Proveedor

Toca el selector de proveedor en Configuración para cambiar entre proveedores. Cada proveedor mantiene sus propios:

- Claves API
- URL base (editable para proxies/autoalojado)
- Lista de modelos

---

## Claves API

### Múltiples Claves por Proveedor

Cada proveedor admite múltiples claves API con nombre. Esto permite:

- **Rotación** — cambiar entre claves para diferentes niveles de uso
- **Organización** — separar uso laboral y personal
- **Respaldo** — mantener una clave de respaldo lista

### Gestionar Claves

1. Ve a **Configuración → Proveedor**
2. Selecciona un proveedor
3. En **Claves API**, toca **Añadir Nueva Clave**
4. Introduce un **nombre** (ej., "Trabajo", "Personal", "Equipo Compartido") y el **valor de la clave**
5. Toca **Añadir**

Toca el botón de radio para establecer la clave activa. Mantén pulsada una clave para **Editar** o **Eliminar**.

### Seguridad de las Claves

!!! warning
    Las claves API se almacenan localmente en una base de datos Room cifrada. Nunca se envían a servidores de 橘子岛 (no los hay). Sin embargo, se exportan en texto plano si las incluyes en un archivo de exportación `.橘子岛`.

---

## Proveedores Personalizados

Añade cualquier endpoint de API compatible con OpenAI:

1. Ve a **Configuración → Proveedor**
2. Toca **+ Añadir Proveedor Personalizado** al final de la lista de proveedores
3. Introduce:
    - **Nombre del Proveedor** — cualquier nombre descriptivo
    - **URL Base** — el endpoint de la API
4. Toca **Añadir**

橘子岛 obtiene la lista de modelos desde `{base_url}/v1/models`. Una vez añadidos, los proveedores personalizados funcionan exactamente como los integrados: añade claves API, sincroniza modelos y chatea.

### Casos de Uso

- **Autoalojado** — conectar con vLLM, LocalAI, text-generation-webui u otros servidores compatibles con OpenAI
- **Proxies** — enrutar a través de un proxy corporativo o puerta de enlace API
- **Endpoints alternativos** — usar Azure OpenAI, Cloudflare AI Gateway u otros servicios compatibles

### Renombrar o Eliminar

Mantén pulsado un proveedor personalizado para **Renombrar** o **Eliminar**. Eliminar quita el proveedor y todas sus claves.

!!! warning
    Los proveedores integrados no pueden ser renombrados ni eliminados.

---

## Anulación de URL Base

Cada proveedor (incluidos los integrados) tiene una **URL Base** editable. Esto es útil para:

- **Proxies**: Enrutar a través de `https://my-proxy.example.com/v1`
- **Autoalojado**: Apuntar a tu propia instancia
- **Enrutamiento regional**: Usar endpoints específicos de región

---

## Sincronizar Modelos

Después de añadir claves API, sincroniza la lista de modelos:

1. Ve a **Configuración → Modelos**
2. Toca **Sincronizar de Todos los Proveedores**
3. 橘子岛 obtiene los modelos disponibles de cada proveedor configurado

Un snackbar muestra el progreso y los resultados de la sincronización. Luego puedes habilitar/deshabilitar modelos individuales y establecer un predeterminado.

---

## Notas Específicas por Proveedor

### Google Gemini

- Claves API de [Google AI Studio](https://aistudio.google.com/apikey)
- Nivel gratuito disponible con límites de tasa
- Soporta ejecución de código y conexión a búsqueda (herramientas integradas)

### OpenAI

- Claves API de [Platform](https://platform.openai.com/api-keys)
- Los modelos de razonamiento (o1, o3) requieren acceso API específico
- Streaming, herramientas y visión todos soportados

### Anthropic

- Claves API de [Console](https://console.anthropic.com/)
- Razonamiento extendido con presupuestos de tokens configurables
- Uso de herramientas con llamadas paralelas soportado

### Ollama

- No requiere clave API (red local)
- URL base típicamente `http://<host>:11434/v1`
- Lista de modelos obtenida de la API de Ollama
- Consulta las [Preguntas Frecuentes](faq.md) para solución de problemas específicos de Ollama

### OpenRouter

- Una sola clave API para más de 200 modelos
- Precios de pago por token varían según el modelo
- Bueno para probar diferentes modelos sin cuentas individuales de proveedor

### Local (llama.cpp)

- No requiere red
- Archivos de modelo GGUF almacenados en el dispositivo
- Consulta [Modelos Locales](local-model.md) para configuración
