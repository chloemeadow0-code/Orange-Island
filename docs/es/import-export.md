# Portabilidad de Datos

橘子岛 almacena todos tus datos en el dispositivo y proporciona capacidades completas de importación/exportación. Tú eres dueño de tus datos — muévelos hacia adentro, hacia afuera, haz copias de seguridad.

## Exportar

Exporta tus datos a un único archivo `.橘子岛` — un archivo portátil que contiene todo lo que 橘子岛 almacena.

### Qué Se Exporta

Tú eliges qué incluir:

| Categoría | Contenido |
|-----------|-----------|
| **Conversaciones y Mensajes** | Todo el historial de chat, árboles de mensajes, ramas |
| **Memorias** | Memoria activa y todos los archivos de memoria guardados |
| **Prompts del Sistema** | Todas las plantillas de prompts del sistema personalizadas |
| **Configuración** | Configuración y preferencias de la aplicación |
| **Claves API** | Todas las claves API configuradas |

!!! danger "Advertencia de Claves API"
    Las claves API se exportan en **texto plano**. Cualquiera con el archivo `.橘子岛` puede leer tus claves. Solo habilita la exportación de claves API si confías en el destino y manejas el archivo de forma segura.

### Cómo Exportar

1. Ve a **Configuración → Control de Datos**
2. Toca **Exportar Datos**
3. Selecciona qué categorías incluir
4. Toca **Exportar**
5. Elige dónde guardar el archivo `.橘子岛`

---

## Importar

Restaura datos desde una exportación `.橘子岛` previa.

### Estrategias de Importación

Al importar, eliges cómo maneja 橘子岛 los datos que ya existen en tu dispositivo:

| Estrategia | Comportamiento |
|------------|----------------|
| **Fusionar** | Añade nuevos elementos, mantiene los existentes. Si un elemento con el mismo ID existe, la versión importada lo sobrescribe. |
| **Reemplazar** | Borra todos los datos existentes en las categorías seleccionadas, luego importa. Un comienzo limpio. |
| **Omitir** | Solo importa elementos que no tienen conflicto. Los elementos existentes no se tocan. |

!!! tip
    Usa **Fusionar** para la mayoría de los casos — añade datos nuevos de forma segura mientras preserva lo que ya está en tu dispositivo.

### Cómo Importar

1. Ve a **Configuración → Control de Datos**
2. Toca **Importar Datos**
3. Selecciona un archivo `.橘子岛`
4. Revisa la vista previa de importación — ve qué hay en el archivo (fecha de exportación, versión, conteos de contenido)
5. Elige una estrategia de importación
6. Toca **Importar**

!!! danger "Advertencia de Claves API"
    Si el archivo de exportación contiene claves API, 橘子岛 te advierte antes de importar. Las claves se importan en texto plano. Solo procede si confías en la fuente del archivo.

---

## Importación de Terceros

Importa conversaciones desde otras plataformas de chat de IA.

Tanto Claude como ChatGPT exportan tus datos como un **archivo `.zip`**. 橘子岛 importa ese `.zip` directamente — no necesitas descomprimirlo primero, y 橘子岛 **no** acepta archivos `.json` sueltos.

### Importar desde Claude

**1. Exportar desde Claude.** Ve a [Claude](https://claude.ai/) → **Configuración → Controles de Datos → Exportar datos**. Claude prepara el archivo rápidamente — generalmente en **menos de una hora** — y te envía un enlace de descarga por correo electrónico.

!!! warning "Descarga rápidamente"
    El enlace de descarga de Claude **caduca rápidamente**. Descarga el `.zip` tan pronto como llegue el correo — si esperas demasiado, el enlace caduca y tendrás que solicitar una nueva exportación.

**2. Importar en 橘子岛.**

1. Ve a **Configuración → Control de Datos → Terceros → Importar desde Claude**
2. Selecciona el archivo `.zip` exportado
3. Revisa la vista previa — ve el conteo de conversaciones y mensajes
4. Elige la estrategia **Fusionar** o **Reemplazar**
5. Toca **Importar**

!!! note
    橘子岛 lee los datos de conversación directamente del archivo `.zip` de exportación de Claude. Los adjuntos se detectan y se muestran en la vista previa, pero solo se importa el texto del mensaje — los archivos adjuntos en sí no se importan.

### Importar desde ChatGPT

**1. Exportar desde ChatGPT.** Ve a [ChatGPT](https://chatgpt.com/) → **Configuración → Controles de Datos → Exportar datos**. ChatGPT procesa la solicitud y te envía un enlace de descarga por correo electrónico cuando está listo.

!!! info "Ten paciencia"
    La exportación de ChatGPT típicamente tarda **1–2 días** en llegar. Esto es normal — espera el correo en lugar de volver a solicitar.

**2. Importar en 橘子岛.**

1. Ve a **Configuración → Control de Datos → Terceros → Importar desde ChatGPT**
2. Selecciona el archivo `.zip` descargado
3. Revisa la vista previa
4. Elige la estrategia **Fusionar** o **Reemplazar**
5. Toca **Importar**

!!! note
    Tanto los mensajes de usuario como los del asistente se importan. Los roles de los mensajes se conservan.

---

## Formato de Archivo

El archivo `.橘子岛` es un archivo basado en JSON. Si tienes inclinación técnica, puedes inspeccionarlo o procesarlo con herramientas estándar. El formato está diseñado para compatibilidad hacia adelante y hacia atrás.

---

## Copia de Seguridad Automática

橘子岛 puede hacer copias de seguridad de tus datos automáticamente según un horario. No necesitas recordar exportar — 橘子岛 lo maneja por ti.

### Cómo Funciona

- La copia de seguridad automática se ejecuta periódicamente en segundo plano usando Android WorkManager
- Cuando corresponde una copia de seguridad, 橘子岛 exporta tus categorías seleccionadas al directorio configurado
- Aparece una notificación solo si una copia de seguridad falla — las copias exitosas son silenciosas
- Las copias de seguridad antiguas se eliminan automáticamente según tu configuración de retención

### Configuración

1. Ve a **Configuración → Control de Datos → Copia de Seguridad Automática**
2. Activa/desactiva **Copia de Seguridad Automática**
3. Establece **Frecuencia** — elige 1 día, 3 días, 5 días, 1 semana o 1 mes
4. Elige **Contenido de exportación** — selecciona qué categorías incluir. Las claves API **pueden** incluirse (se muestra una advertencia cuando marcas esa casilla) — solo actívalo si la ubicación de la copia de seguridad es privada y segura. Las claves API **no** se incluyen por defecto.
5. Establece **Ubicación de copia de seguridad** — toca para elegir una carpeta (por defecto `Download/橘子岛/Backup`)
6. Activa/desactiva **Eliminar automáticamente copias antiguas**, y establece el período **Eliminar más antiguas de**

!!! info "Restricción de Eliminación Automática"
    El período de eliminación debe ser mayor que el período de copia de seguridad. Por ejemplo, si haces copias cada semana, las copias pueden eliminarse automáticamente después de 1 mes o 1 año — nunca antes. Esto evita eliminar tu única copia de seguridad antes de que se cree una nueva.

!!! note
    La copia de seguridad automática usa Android WorkManager para garantizar fiabilidad incluso si la aplicación se cierra o el dispositivo se reinicia. Las copias de seguridad pueden retrasarse ligeramente durante el modo Doze para conservar batería.

---

## Mejores Prácticas

- **Exporta regularmente** como copia de seguridad — guarda el archivo en un lugar seguro
- **Habilita la Copia de Seguridad Automática** para protección programada sin intervención
- **No incluyas claves API** en exportaciones rutinarias — habilita la exportación de claves solo para migraciones completas de dispositivo
- **Usa Fusionar para importaciones incrementales** — Reemplazar es destructivo
- **Previsualiza antes de importar** — verifica la fecha de exportación y los conteos de contenido para confirmar que es el archivo correcto
