# Modelos

Gestiona qué modelos de IA están disponibles y establece tu modelo predeterminado para las conversaciones.

## Lista de Modelos

La página de **Modelos** muestra todos los modelos que 橘子岛 conoce, organizados por proveedor:

- **Modelo Predeterminado** — El modelo usado para nuevas conversaciones. Toca para cambiar.
- **Modelos Disponibles** — Expande cada proveedor para ver sus modelos. Habilita los que quieras usar.

### Habilitar / Deshabilitar Modelos

Marca o desmarca la casilla junto a un modelo para alternar su disponibilidad. Los modelos deshabilitados no aparecerán en el selector de modelos en las conversaciones.

### Renombrar Modelos

Toca el icono de edición (lápiz) junto a un modelo para darle un alias personalizado. Este alias aparece en toda la aplicación en lugar del ID técnico del modelo.

### Sincronizar Modelos

Toca **Sincronizar Modelos** para obtener los últimos modelos disponibles de todos los proveedores de API configurados. Esto requiere una conexión a internet y claves API válidas.

!!! tip "Modelos Locales"
    Los modelos locales aparecen en la sección del proveedor **Local**. Se gestionan por separado en **Configuración → Proveedores → Local**.

---

## Modelo Predeterminado

El **Modelo Predeterminado** se usa para todas las conversaciones nuevas. Para cambiarlo:

1. Toca la fila del modelo predeterminado en la parte superior de la página de Modelos
2. Selecciona un modelo de la lista (solo se muestran los modelos habilitados)
3. El cambio surte efecto inmediatamente

Puedes anular el modelo por conversación desde el selector de modelo de la pantalla de chat.

---

## Alias de Modelos

Los alias de modelos te permiten dar nombres amigables a modelos con IDs técnicos largos. Por ejemplo, puedes renombrar `openai/gpt-4o-mini` a simplemente "GPT-4o Mini".

Los alias se muestran en todas partes: el selector de modelos, las cabeceras de conversación y las páginas de configuración.

Para eliminar un alias, borra el campo de texto y guarda.

---

## Solución de Problemas

### Los modelos no aparecen

- Toca **Sincronizar Modelos** para actualizar la lista
- Verifica que tienes una clave API válida para el proveedor en **Configuración → Proveedores**
- Comprueba tu conexión a internet
- Algunos proveedores pueden estar temporalmente no disponibles

### Los modelos locales no se muestran

- Importa un archivo de modelo GGUF en **Configuración → Proveedores → Local**
- El modelo debe estar en formato GGUF válido
