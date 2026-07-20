# Búsqueda Web

Permite al modelo buscar en internet y obtener páginas web en tiempo real. Cuando está habilitado, el modelo puede buscar información actual, verificar hechos, recuperar documentación o investigar temas — todo de forma autónoma a través de llamadas a herramientas.

## Proveedores Soportados

| Proveedor | Descripción | Nivel Gratuito | Configuración |
|-----------|-------------|----------------|---------------|
| **DuckDuckGo Lite** | Anónimo, sin necesidad de clave API | Sí (ilimitado, mejor esfuerzo) | Sin configuración — funciona de inmediato |
| **Brave** | API de búsqueda centrada en privacidad | Sí (2,000 consultas/mes) | [api.search.brave.com](https://api.search.brave.com/) |
| **Serper** | API rápida de Google Search | Sí (2,500 consultas/mes) | [serper.dev](https://serper.dev) |
| **Tavily** | Búsqueda optimizada para IA, diseñada para agentes LLM | Sí (1,000 consultas/mes) | [tavily.com](https://tavily.com) |
| **SearXNG** | Metabuscador autoalojado | Autoalojado (ilimitado) | Tu propia instancia |

## Configuración

### DuckDuckGo Lite

DuckDuckGo Lite es el proveedor de búsqueda **predeterminado** — no requiere clave API, funciona de inmediato.

1. En 橘子岛, ve a **Configuración → Búsqueda Web**
2. Selecciona **DuckDuckGo Lite** como proveedor de búsqueda
3. No se necesita clave ni URL — comienza a buscar de inmediato

!!! note "Servicio de mejor esfuerzo"
    DuckDuckGo Lite utiliza scraping HTML de `lite.duckduckgo.com`. DDG puede cambiar su diseño, aplicar límites de tasa o bloquear solicitudes automatizadas. Se ofrece explícitamente como una opción sin clave y de mejor esfuerzo. Si necesitas fiabilidad, configura uno de los proveedores basados en API a continuación.

### Brave

1. Obtén una clave API de [Brave Search API](https://api.search.brave.com/)
2. En 橘子岛, ve a **Configuración → Búsqueda Web**
3. Selecciona **Brave** como proveedor de búsqueda
4. Pega tu clave API

### Serper

1. Obtén una clave API de [serper.dev](https://serper.dev)
2. En 橘子岛, ve a **Configuración → Búsqueda Web**
3. Selecciona **Serper**
4. Pega tu clave API

### Tavily

1. Obtén una clave API de [tavily.com](https://tavily.com)
2. En 橘子岛, ve a **Configuración → Búsqueda Web**
3. Selecciona **Tavily**
4. Pega tu clave API

### SearXNG

1. Configura una instancia de SearXNG (autoalojada) o usa una instancia pública
2. En 橘子岛, ve a **Configuración → Búsqueda Web**
3. Selecciona **SearXNG**
4. Introduce la **URL Base** de tu instancia (ej., `https://searx.be`)
5. La clave API es opcional (solo se necesita si tu instancia requiere autenticación)

!!! warning "Instancias Públicas"
    Las instancias públicas de SearXNG a menudo tienen límites de tasa o son poco fiables. Se recomienda el autoalojamiento para un uso consistente.

---

## Configuración

### Máximo de Resultados

Establece cuántos resultados de búsqueda obtener por consulta: **1–10**. El valor predeterminado depende del dispositivo. Más resultados dan al modelo más contexto pero cuestan más tokens.

### Habilitar/Deshabilitar

Alterna **Habilitar Búsqueda Web** en la página de configuración de Búsqueda Web. Cuando está deshabilitado, el modelo no puede llamar a la herramienta de búsqueda web.

---

## Cómo Usa el Modelo la Búsqueda

Cuando haces una pregunta que necesita información actual o externa, el modelo llama automáticamente a la búsqueda web:

1. **Buscar**: El modelo llama a la API de búsqueda con una consulta que formula
2. **Obtener**: El modelo puede opcionalmente obtener el contenido completo de la página de las URLs de resultados
3. **Sintetizar**: El modelo lee los resultados y los integra en su respuesta

Verás cada búsqueda y obtención como tarjetas de herramienta en línea en la conversación.

### Ejemplo

```text
Tú: "¿Cuál es la última versión de Python?"
Modelo: [Busca "última versión de Python 2026"]
        [Lee el resultado]
        "Python 3.14.0 fue lanzado en octubre de 2025..."
```

---

## Obtención de Páginas Web

Más allá de la búsqueda, el modelo puede obtener y leer páginas web específicas. Cuando el modelo encuentra una URL en los resultados de búsqueda, puede llamar a `web_fetch` para recuperar el contenido completo de la página:

- El contenido obtenido se convierte a markdown
- El modelo lo procesa y extrae información relevante
- Los resultados de la obtención se muestran como tarjetas de herramienta

---

## Consideraciones de Privacidad

Al usar la búsqueda web:

- Tus consultas van al proveedor de búsqueda (Brave, Serper, etc.), no a 橘子岛
- 橘子岛 no registra ni almacena tus consultas de búsqueda (excepto en la conversación misma)
- El autoalojamiento de SearXNG te da la mayor privacidad — las consultas permanecen en tu infraestructura
