# Memoria y Caché

橘子岛 tiene un sistema de memoria persistente que permite al modelo recordar información a través de las conversaciones. Combinado con el almacenamiento en caché automático basado en embeddings, proporciona una base de conocimiento que crece con tu uso.

## Tipos de Memoria

### Memoria Activa

Un único contexto de memoria siempre activo que se incluye con **cada llamada API** al modelo. Piensa en ello como una nota adhesiva que el modelo siempre ve.

**Usa la Memoria Activa para:**
- Tu nombre, preferencias y antecedentes
- Contexto del proyecto que el modelo siempre debe conocer
- Instrucciones permanentes que se aplican a todas las conversaciones
- Hechos que estás cansado de repetir

**Ejemplo de contenido de Memoria Activa:**
```text
Usuario: Newo Ether
Preferencias: Prefiere chino para chat informal, inglés para temas técnicos.
Proyecto: Construyendo 橘子岛 — un cliente LLM Android BYOK.
Estilo de código: Kotlin, Jetpack Compose, arquitectura MVVM.
```

#### Editar la Memoria Activa

1. Ve a **Configuración → Memoria**
2. Desplázate hasta **Memoria Activa**
3. Toca **Editar Memoria Activa**
4. Introduce tu contenido
5. Toca **Guardar**

El modelo también puede actualizar la memoria activa mediante llamadas a herramientas si **Acceder a Memoria Activa** está habilitado.

---

### Memorias Guardadas

Una colección de archivos de memoria con nombre que el modelo puede buscar, leer, crear, editar y eliminar. A diferencia de la Memoria Activa (siempre enviada), las memorias guardadas se recuperan bajo demanda.

**Usa las Memorias Guardadas para:**
- Material de referencia (documentación de API, detalles de configuración, comandos)
- Notas específicas de proyecto
- Aprendizajes e ideas de conversaciones pasadas
- Cualquier cosa que quieras que el modelo recuerde cuando sea relevante

#### Crear Memorias Manualmente

1. Ve a **Configuración → Memoria**
2. Toca **Añadir Memoria**
3. Introduce:
    - **Título** — nombre descriptivo
    - **Descripción** — breve resumen (usado para coincidencia de búsqueda)
    - **Contenido** — el contenido completo de la memoria
4. Toca **Crear**

#### Memorias Creadas por el Modelo

Cuando **Acceder a Memorias Guardadas** está habilitado, el modelo puede crear, leer, actualizar y eliminar archivos de memoria mediante llamadas a herramientas. Esto permite al modelo:

- Recordar hechos que le cuentas
- Guardar fragmentos de código útiles o configuraciones
- Construir una base de conocimiento con el tiempo
- Limpiar información obsoleta

---

## Permisos de Memoria

Controla a qué puede acceder el modelo:

| Configuración | Ubicación | Cuándo Habilitar |
|---------------|-----------|------------------|
| **Acceder a Memorias Guardadas** | Configuración → Memoria | Quieres que el modelo lea/escriba archivos de memoria |
| **Acceder a Memoria Activa** | Configuración → Memoria | Quieres que el modelo actualice el contexto persistente |
| **Acceder a Conversaciones Pasadas** | Configuración → Búsqueda de Conversaciones | Quieres que el modelo busque en el historial de chat |

Los tres están **desactivados** por defecto. Habilita solo lo que necesites.

---

## Auto-Caché

El auto-caché genera automáticamente embeddings para los nuevos mensajes a medida que llegan. Esto mantiene tu índice de búsqueda de conversaciones actualizado sin intervención manual.

### Habilitar Auto-Caché

1. Ve a **Configuración → Búsqueda de Conversaciones**
2. Elige un modelo de embedding (si aún no lo has hecho — consulta [Embedding / RAG](embedding.md))
3. En **Caché**, activa **Auto-caché para nuevos mensajes**

Cuando está habilitado, cada nuevo mensaje (usuario y modelo) se incrusta e indexa automáticamente para búsqueda semántica.

### Caché Manual

Si el auto-caché está desactivado, puedes almacenar mensajes manualmente:

1. Ve a **Configuración → Búsqueda de Conversaciones**
2. Toca **Cachear** — calcula embeddings para todos los mensajes no cacheados
3. El progreso se muestra como un indicador circular

Toca **Re-cachear** para reconstruir todo el índice desde cero. Esto elimina todos los embeddings cacheados y vuelve a procesar cada mensaje. Úsalo cuando:
- Has cambiado de modelo de embedding
- El caché parece corrupto o desactualizado
- Los resultados de búsqueda son inesperadamente pobres

!!! warning
    Re-cachear es irreversible y puede tardar un tiempo dependiendo de la cantidad de mensajes y la velocidad del modelo de embedding.

### Estado del Caché

La configuración del modelo de embedding muestra cuántos mensajes están cacheados vs. no cacheados:
- **"Todos los N mensajes cacheados"** — actualizado
- **"X de Y mensajes no cacheados"** — trabajo pendiente por procesar

---

## Llamadas a Herramientas de Memoria en el Chat

Cuando el modelo usa herramientas de memoria, verás tarjetas en línea:

| Herramienta | Texto de la Tarjeta |
|-------------|---------------------|
| Buscar | "Buscó en N memorias guardadas" |
| Leer | "Leyó [nombre de memoria]" |
| Guardar | "Guardó [nombre de memoria]" |
| Editar | "Actualizó [nombre de memoria]" |
| Eliminar | "Eliminó [nombre de memoria]" |
| Actualizar Activa | "Actualizó la memoria activa" |

Toca cualquier tarjeta para ver el contenido completo que fue leído o escrito.

---

## Mejores Prácticas

- **Mantén la Memoria Activa concisa** — se incluye en cada llamada API, así que el contenido extenso desperdicia tokens
- **Usa títulos descriptivos para las Memorias Guardadas** — los títulos ayudan al modelo a encontrar la memoria correcta
- **Habilita el auto-caché** si usas la búsqueda de conversaciones regularmente
- **Re-cachea después de cambiar de modelo de embedding** — diferentes modelos producen embeddings incompatibles
